package com.example.cutline.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.example.cutline.model.KeyframeEasing
import com.example.cutline.model.LayerTransform
import com.example.cutline.model.StabilizationSettings
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TransformKeyframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.max
import kotlin.math.min

/** Sparse dense-optical-flow camera path analysis; resulting transforms are applied in preview and export. */
@OptIn(UnstableApi::class)
class StabilizationAnalyzer(private val context: Context) {
    suspend fun analyze(clip: TimelineClip, settings: StabilizationSettings): List<TransformKeyframe> =
        withContext(Dispatchers.IO) {
            check(loadOpenCv()) { "OpenCV could not be loaded on this device" }
            val duration = clip.sourceTrimDurationMs.coerceAtMost(120_000L)
            val frameCount = (duration / 120L).coerceIn(2L, 180L).toInt()
            val intervalMs = duration.toDouble() / (frameCount - 1)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, clip.source)
            val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1920f
            val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1080f
            val width = 128
            val height = (width * sourceHeight / sourceWidth).toInt().coerceIn(48, 128)
            var previous: Mat? = null
            val pathX = FloatArray(frameCount)
            val pathY = FloatArray(frameCount)
            val times = LongArray(frameCount)
            var decodedFrames = 0
            try {
                for (index in 0 until frameCount) {
                    val relativeMs = (index * intervalMs).toLong().coerceAtMost(duration)
                    times[index] = relativeMs
                    val bitmap = retriever.getFrameAtTime(
                        (clip.sourceStartMs + relativeMs) * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: continue
                    decodedFrames++
                    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    if (scaled !== bitmap) bitmap.recycle()
                    val rgba = Mat()
                    val gray = Mat()
                    Utils.bitmapToMat(scaled, rgba)
                    Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                    rgba.release()
                    scaled.recycle()
                    previous?.let { old ->
                        val flow = Mat()
                        Video.calcOpticalFlowFarneback(old, gray, flow, .5, 3, 15, 3, 5, 1.2, 0)
                        val xMotion = ArrayList<Float>(128)
                        val yMotion = ArrayList<Float>(128)
                        for (y in 8 until height - 8 step 8) for (x in 8 until width - 8 step 8) {
                            val vector = flow.get(y, x) ?: continue
                            if (vector.size >= 2 && vector[0].isFinite() && vector[1].isFinite()) {
                                xMotion += vector[0].toFloat()
                                yMotion += vector[1].toFloat()
                            }
                        }
                        flow.release()
                        pathX[index] = pathX[index - 1] + median(xMotion).coerceIn(-12f, 12f)
                        pathY[index] = pathY[index - 1] + median(yMotion).coerceIn(-12f, 12f)
                    }
                    previous?.release()
                    previous = gray
                }
            } finally {
                previous?.release()
                retriever.release()
            }
            check(decodedFrames >= 2) { "Could not decode enough video frames to analyze stabilization" }
            val smoothX = smooth(pathX, radius = 5)
            val smoothY = smooth(pathY, radius = 5)
            val cropScale = 1f / (1f - settings.cropPercent.coerceIn(0f, 35f) / 100f)
            buildList {
                for (index in times.indices) {
                    val correctionX = (smoothX[index] - pathX[index]) * (sourceWidth / width) * settings.strength.coerceIn(0f, 1f)
                    val correctionY = (smoothY[index] - pathY[index]) * (sourceHeight / height) * settings.strength.coerceIn(0f, 1f)
                    add(TransformKeyframe(
                        timeMs = times[index],
                        transform = LayerTransform(scaleX = cropScale, scaleY = cropScale, positionX = correctionX, positionY = correctionY),
                        easing = KeyframeEasing.LINEAR
                    ))
                }
            }
        }

    private fun loadOpenCv(): Boolean = runCatching { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); true }.getOrDefault(false)

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun smooth(values: FloatArray, radius: Int): FloatArray = FloatArray(values.size) { index ->
        val start = max(0, index - radius)
        val end = min(values.lastIndex, index + radius)
        values.copyOfRange(start, end + 1).average().toFloat()
    }
}
