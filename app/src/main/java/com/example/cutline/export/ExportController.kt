package com.example.cutline.export

import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.core.content.ContextCompat
import com.example.cutline.engine.BuiltComposition
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
data class ExportOptions(
    val videoMimeType: String = MimeTypes.VIDEO_H264,
    val audioMimeType: String = MimeTypes.AUDIO_AAC,
    val videoBitrate: Int = 12_000_000,
    val audioBitrate: Int = 192_000,
    val expectedWidth: Int = 1080,
    val expectedHeight: Int = 1920,
    val expectedFrameRate: Int = 30,
    val colorMode: ExportColorMode = ExportColorMode.SDR
)

enum class ExportColorMode { SDR, HDR10, HLG }

enum class ExportPreset(val label: String, val options: ExportOptions) {
    YOUTUBE_1080("YouTube 1080p", ExportOptions(expectedWidth = 1920, expectedHeight = 1080, videoBitrate = 12_000_000)),
    YOUTUBE_4K("YouTube 4K", ExportOptions(expectedWidth = 3840, expectedHeight = 2160, videoBitrate = 45_000_000)),
    TIKTOK("TikTok / Shorts", ExportOptions(expectedWidth = 1080, expectedHeight = 1920, videoBitrate = 12_000_000)),
    INSTAGRAM("Instagram Reel", ExportOptions(expectedWidth = 1080, expectedHeight = 1920, videoBitrate = 10_000_000)),
    SQUARE("Square", ExportOptions(expectedWidth = 1080, expectedHeight = 1080, videoBitrate = 10_000_000))
}

@Singleton
@OptIn(UnstableApi::class)
class ExportController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val historyStore = ExportHistoryStore(context)
    private val _snapshot = MutableStateFlow(ExportSnapshot())
    val snapshot: StateFlow<ExportSnapshot> = _snapshot
    val history: List<ExportHistoryEntry> get() = historyStore.load()

    @Volatile private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null
    private var activeOptions = ExportOptions()

    fun start(
        composition: BuiltComposition,
        outputFile: File,
        options: ExportOptions = ExportOptions()
    ) {
        check(_snapshot.value.status != ExportStatus.EXPORTING) {
            "An export is already running"
        }
        val capability = ExportCapabilities.check(
            options.videoMimeType,
            options.expectedWidth,
            options.expectedHeight,
            options.expectedFrameRate
        )
        require(capability.supported) { capability.reason ?: "Export format is not supported" }
        outputFile.parentFile?.mkdirs()
        activeOptions = options
        _snapshot.value = ExportSnapshot(
            status = ExportStatus.EXPORTING,
            outputPath = outputFile.absolutePath
        )

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(options.videoMimeType)
            .setAudioMimeType(options.audioMimeType)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(options.videoBitrate)
                            .build()
                    )
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder()
                            .setBitrate(options.audioBitrate)
                            .build()
                    )
                    .build()
            )
            .build()

        activeTransformer = transformer
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult
            ) {
                if (_snapshot.value.status != ExportStatus.EXPORTING) return
                progressJob?.cancel()
                val verification = verifyOutput(outputFile, options)
                if (!verification.valid) {
                    fail(verification.reason ?: "Export verification failed")
                } else {
                    historyStore.append(ExportHistoryEntry(outputFile.absolutePath, System.currentTimeMillis(), options.expectedWidth, options.expectedHeight, ExportStatus.COMPLETE))
                    _snapshot.value = _snapshot.value.copy(
                        status = ExportStatus.COMPLETE,
                        progress = 100
                    )
                }
                activeTransformer = null
            }

            override fun onError(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (_snapshot.value.status != ExportStatus.EXPORTING) return
                fail(exportException.message ?: "Export failed")
                activeTransformer = null
            }
        })

        transformer.start(composition.composition, outputFile.absolutePath)
        ContextCompat.startForegroundService(
            context,
            Intent(context, ExportService::class.java)
        )
        progressJob = scope.launch {
            val holder = ProgressHolder()
            while (_snapshot.value.status == ExportStatus.EXPORTING) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _snapshot.value = _snapshot.value.copy(progress = holder.progress)
                }
                delay(250)
            }
        }
    }

    fun cancel() {
        if (_snapshot.value.status != ExportStatus.EXPORTING) return
        activeTransformer?.cancel()
        progressJob?.cancel()
        _snapshot.value = _snapshot.value.copy(
            status = ExportStatus.CANCELLED,
            progress = 0
        )
    }

    fun fastTrimSingleClip(source: Uri, startMs: Long, endMs: Long, outputFile: File) {
        check(_snapshot.value.status != ExportStatus.EXPORTING) { "An export is already running" }
        _snapshot.value = ExportSnapshot(ExportStatus.EXPORTING, 0, outputFile.absolutePath)
        scope.launch(Dispatchers.IO) {
            val result = FastTrimExporter.export(context, source, startMs, endMs, outputFile)
            scope.launch {
                result.onSuccess {
                    _snapshot.value = ExportSnapshot(ExportStatus.COMPLETE, 100, outputFile.absolutePath)
                    historyStore.append(ExportHistoryEntry(outputFile.absolutePath, System.currentTimeMillis(), 0, 0, ExportStatus.COMPLETE, "Fast single-clip trim"))
                }.onFailure { fail("Fast trim failed: ${it.message ?: "unknown error"}") }
            }
        }
    }

    fun reset() {
        if (_snapshot.value.status != ExportStatus.EXPORTING) {
            _snapshot.value = ExportSnapshot()
        }
    }

    private fun fail(message: String) {
        progressJob?.cancel()
        _snapshot.value = _snapshot.value.copy(
            status = ExportStatus.FAILED,
            progress = 0,
            error = message
        )
        _snapshot.value.outputPath?.let { path ->
            historyStore.append(ExportHistoryEntry(path, System.currentTimeMillis(), activeOptions.expectedWidth, activeOptions.expectedHeight, ExportStatus.FAILED, message))
        }
    }

    private fun verifyOutput(file: File, options: ExportOptions): Verification {
        if (!file.isFile || file.length() == 0L) {
            return Verification(false, "Export produced an empty file")
        }
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var videoFound = false
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/")) continue
                videoFound = true
                if (mime != options.videoMimeType) {
                    return Verification(false, "Expected ${options.videoMimeType}, got $mime")
                }
                val width = format.getInteger(MediaFormat.KEY_WIDTH, 0)
                val height = format.getInteger(MediaFormat.KEY_HEIGHT, 0)
                if (width != options.expectedWidth || height != options.expectedHeight) {
                    return Verification(
                        false,
                        "Expected ${options.expectedWidth}x${options.expectedHeight}, got ${width}x${height}"
                    )
                }
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    val frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    if (frameRate != options.expectedFrameRate) {
                        return Verification(
                            false,
                            "Expected ${options.expectedFrameRate}fps, got ${frameRate}fps"
                        )
                    }
                }
            }
            if (!videoFound) Verification(false, "Export contains no video track")
            else Verification(true, null)
        } catch (error: Exception) {
            Verification(false, "Export verification failed: ${error.message ?: "unknown error"}")
        } finally {
            extractor.release()
        }
    }

    private data class Verification(val valid: Boolean, val reason: String?)
}
