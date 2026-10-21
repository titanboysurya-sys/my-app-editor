package com.example.cutline.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

object ThumbnailCreator {
    fun create(context: Context, source: Uri, timeMs: Long, output: File, width: Int = 1280): Result<File> = runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, source)
        val original = retriever.getFrameAtTime(timeMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("Unable to capture a frame")
        retriever.release()
        val height = (original.height * width.toFloat() / original.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createScaledBitmap(original, width, height, true)
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (bitmap !== original) bitmap.recycle()
        if (!original.isRecycled) original.recycle()
        output
    }
}
