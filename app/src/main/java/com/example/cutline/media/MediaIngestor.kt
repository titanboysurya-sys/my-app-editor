package com.example.cutline.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaAsset(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val hasVideo: Boolean,
    val hasAudio: Boolean
)

class MediaIngestor(private val resolver: ContentResolver) {
    suspend fun inspect(uri: Uri): MediaAsset = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(resolver, uri)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            MediaAsset(
                uri = uri,
                displayName = uri.lastPathSegment ?: "Imported media",
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 3_000L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0,
                hasVideo = mime.startsWith("video/") || mime.startsWith("image/"),
                hasAudio = mime.startsWith("audio/") || mime.startsWith("video/")
            )
        } finally {
            retriever.release()
        }
    }

    suspend fun thumbnail(
        uri: Uri,
        timeMs: Long,
        width: Int = 160,
        height: Int = 90
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(resolver, uri)
            val frame = retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null
            val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
            if (scaled !== frame) frame.recycle()
            scaled
        } finally {
            retriever.release()
        }
    }
}

object MediaPickerTypes {
    const val VIDEO_AUDIO_IMAGE = "video/*,audio/*,image/*"
}
