package com.example.cutline.media

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Lightweight offline envelope extractor. It samples encoded audio packet
 * activity, which is fast enough for a timeline overview. A production build
 * can replace this implementation with MediaCodec PCM decoding without
 * changing the Compose waveform API.
 */
class AudioWaveformExtractor(private val resolver: ContentResolver) {
    suspend fun extract(uri: Uri, bars: Int = 160): List<Float> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(resolver, uri, emptyMap())
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
                    .startsWith("audio/")
            } ?: return@withContext List(bars) { 0f }
            extractor.selectTrack(track)
            val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
            val values = ArrayList<Float>(bars)
            while (values.size < bars) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                var energy = 0f
                val count = size.coerceAtLeast(1)
                for (index in 0 until size) {
                    energy += abs(buffer.get(index).toInt()) / 128f
                }
                values += (energy / count).coerceIn(0f, 1f)
                buffer.clear()
                extractor.advance()
            }
            if (values.isEmpty()) List(bars) { 0f }
            else List(bars) { values[(it * values.size / bars).coerceAtMost(values.lastIndex)] }
        } finally {
            extractor.release()
        }
    }
}
