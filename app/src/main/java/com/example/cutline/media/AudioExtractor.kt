package com.example.cutline.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File

/** Lossless audio-track extraction for sources whose audio is already muxed in a supported container. */
object AudioExtractor {
    fun extract(context: Context, source: Uri, output: File): Result<File> = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, source, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
        } ?: error("The selected media has no audio track")
        output.parentFile?.mkdirs()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        extractor.selectTrack(track)
        val muxTrack = muxer.addTrack(extractor.getTrackFormat(track))
        muxer.start()
        val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxTrack, buffer, info)
            extractor.advance()
        }
        muxer.stop(); muxer.release(); extractor.release()
        require(output.isFile && output.length() > 0L) { "Audio extraction produced an empty file" }
        output
    }
}
