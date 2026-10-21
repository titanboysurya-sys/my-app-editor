package com.example.cutline.export

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fast path for one untouched source clip. It copies encoded samples without re-encoding. */
object FastTrimExporter {
    suspend fun export(context: Context, source: Uri, startMs: Long, endMs: Long, output: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(endMs > startMs) { "Trim range is empty" }
            val extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(source, "r")?.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
            } ?: error("Unable to open source media")
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = IntArray(extractor.trackCount) { -1 }
            for (index in 0 until extractor.trackCount) trackMap[index] = muxer.addTrack(extractor.getTrackFormat(index))
            muxer.start()
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val timeUs = extractor.sampleTime
                if (timeUs < 0L || timeUs > endMs * 1000L) break
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, (timeUs - startMs * 1000L).coerceAtLeast(0L), extractor.sampleFlags)
                muxer.writeSampleData(trackMap[extractor.sampleTrackIndex], buffer, info)
                if (!extractor.advance()) break
            }
            muxer.stop()
            muxer.release()
            extractor.release()
            require(output.isFile && output.length() > 0L) { "Fast trim produced no output" }
            output
        }
    }
}
