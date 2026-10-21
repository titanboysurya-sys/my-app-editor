package com.example.cutline.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import java.io.File

/** Creates a lower-resolution H.264 proxy through the same Media3 pipeline used by export. */
@OptIn(UnstableApi::class)
class ProxyMediaGenerator(private val context: Context) {
    fun generate(source: Uri, output: File, height: Int = 540, onComplete: (Result<File>) -> Unit) {
        output.parentFile?.mkdirs()
        val transformer = Transformer.Builder(context).build()
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                onComplete(if (output.isFile && output.length() > 0L) Result.success(output) else Result.failure(IllegalStateException("Proxy is empty")))
            }
            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                onComplete(Result.failure(exportException))
            }
        })
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(Presentation.createForHeight(height))))
            .build()
        val sequence = androidx.media3.transformer.EditedMediaItemSequence.Builder(
            setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO, androidx.media3.common.C.TRACK_TYPE_AUDIO)
        ).addItem(item).build()
        transformer.start(androidx.media3.transformer.Composition.Builder(listOf(sequence)).build(), output.absolutePath)
    }
}
