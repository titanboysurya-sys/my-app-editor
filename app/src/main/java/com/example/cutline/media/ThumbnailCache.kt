package com.example.cutline.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThumbnailCache(context: Context) {
    private val appContext = context.applicationContext
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    suspend fun get(uri: Uri, timeMs: Long, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$timeMs@$width"
        memory.get(key)?.let { return@withContext it }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null
            val height = (bitmap.height * width.toFloat() / bitmap.width).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (scaled !== bitmap) bitmap.recycle()
            memory.put(key, scaled)
            scaled
        } finally {
            retriever.release()
        }
    }
}
