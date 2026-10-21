package com.example.cutline.export

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Transformer
import com.example.cutline.engine.BuiltComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File

data class BatchExportItem(val composition: BuiltComposition, val output: File, val options: ExportOptions = ExportOptions())
data class BatchExportResult(val output: File, val success: Boolean, val error: String? = null)

/** Sequential batch renderer; one transformer at a time keeps memory and thermal load bounded. */
@OptIn(UnstableApi::class)
class BatchExportQueue(private val context: Context) {
    suspend fun run(items: List<BatchExportItem>, onProgress: (Int) -> Unit = {}): List<BatchExportResult> = withContext(Dispatchers.Main) {
        val results = mutableListOf<BatchExportResult>()
        items.forEachIndexed { index, item ->
            item.output.parentFile?.mkdirs()
            val result = kotlinx.coroutines.suspendCancellableCoroutine<BatchExportResult> { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(item.options.videoMimeType)
                    .setAudioMimeType(item.options.audioMimeType)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context).build())
                    .build()
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        if (continuation.isActive) continuation.resume(
                            BatchExportResult(item.output, item.output.isFile && item.output.length() > 0L), null
                        )
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                        if (continuation.isActive) continuation.resume(BatchExportResult(item.output, false, exportException.message), null)
                    }
                })
                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(item.composition.composition, item.output.absolutePath)
            }
            results += result
            onProgress(((index + 1) * 100) / items.size.coerceAtLeast(1))
        }
        results
    }
}
