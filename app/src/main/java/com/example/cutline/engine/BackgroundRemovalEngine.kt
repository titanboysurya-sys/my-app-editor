package com.example.cutline.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.io.File
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Runs the bundled MediaPipe selfie model and stores a transparent PNG cutout for an image clip. */
class BackgroundRemovalEngine(private val context: Context) {
    fun removeFromImage(source: Uri): Uri {
        val bitmap = decodeOriented(source)
        val segmenter = ImageSegmenter.createFromOptions(
            context,
            ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(false)
                .setOutputConfidenceMasks(true)
                .build()
        )

        try {
            val result = segmenter.segment(BitmapImageBuilder(bitmap).build())
            val confidenceMask = result.confidenceMasks().orElse(emptyList()).firstOrNull()
                ?: error("The segmentation model returned no foreground mask")
            val maskBuffer = ByteBufferExtractor.extract(confidenceMask)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            val maskWidth = confidenceMask.width
            val maskHeight = confidenceMask.height
            require(maskWidth > 0 && maskHeight > 0 && maskBuffer.remaining() >= maskWidth * maskHeight) {
                "The segmentation model returned an invalid mask"
            }

            val mask = FloatArray(maskWidth * maskHeight)
            maskBuffer.get(mask)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (y in 0 until bitmap.height) {
                val maskY = (y * maskHeight / bitmap.height).coerceIn(0, maskHeight - 1)
                for (x in 0 until bitmap.width) {
                    val maskX = (x * maskWidth / bitmap.width).coerceIn(0, maskWidth - 1)
                    val alpha = (mask[maskY * maskWidth + maskX].coerceIn(0f, 1f) * 255f).roundToInt()
                    val index = y * bitmap.width + x
                    pixels[index] = (alpha shl 24) or (pixels[index] and 0x00FFFFFF)
                }
            }
            bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val outputDirectory = File(context.filesDir, "media/background-removal")
            check(outputDirectory.exists() || outputDirectory.mkdirs()) { "Could not create cutout storage" }
            val output = File(outputDirectory, "cutout-${UUID.randomUUID()}.png")
            output.outputStream().buffered().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Could not encode transparent cutout" }
            }
            return Uri.fromFile(output)
        } finally {
            segmenter.close()
            bitmap.recycle()
        }
    }

    private fun decodeOriented(source: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot open selected image" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected media is not a readable image" }
        val largestSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (largestSide / sampleSize > MAX_SIDE) sampleSize *= 2
        val decoded = context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot reopen selected image" }
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("Could not decode selected image")

        val orientation = runCatching {
            context.contentResolver.openInputStream(source)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val mirror = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
            orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL
        if (rotation == 0f && !mirror) return decoded
        val matrix = android.graphics.Matrix().apply {
            if (mirror) postScale(-1f, 1f)
            if (rotation != 0f) postRotate(rotation)
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private companion object {
        const val MODEL_ASSET = "selfie_segmenter.tflite"
        const val MAX_SIDE = 2560
    }
}
