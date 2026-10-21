package com.example.cutline.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.UUID

class FreezeFrameCreator(private val context: Context) {
    fun capture(source: Uri, timeMs: Long): Uri {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, source)
            val bitmap = retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: error("Could not decode a frame at the selected time")
            try {
                val directory = File(context.filesDir, "media/freeze-frames")
                check(directory.exists() || directory.mkdirs()) { "Could not create freeze-frame storage" }
                val output = File(directory, "freeze-${UUID.randomUUID()}.png")
                output.outputStream().buffered().use { stream ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Could not save the freeze frame"
                    }
                }
                return Uri.fromFile(output)
            } finally {
                bitmap.recycle()
            }
        } finally {
            retriever.release()
        }
    }
}
