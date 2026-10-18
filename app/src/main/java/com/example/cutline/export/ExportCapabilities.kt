package com.example.cutline.export

import android.media.MediaCodecInfo
import android.media.MediaCodecList

data class ExportCapability(val supported: Boolean, val reason: String? = null)

object ExportCapabilities {
    fun check(mimeType: String, width: Int, height: Int, frameRate: Int): ExportCapability {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        val matching = codecs.filter { info ->
            info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
        if (matching.isEmpty()) return ExportCapability(false, "No hardware encoder supports $mimeType")
        val sizeSupported = matching.any { info ->
            val type = info.supportedTypes.first { it.equals(mimeType, true) }
            info.getCapabilitiesForType(type).videoCapabilities?.isSizeSupported(width, height) == true
        }
        return if (sizeSupported) ExportCapability(true)
        else ExportCapability(false, "${width}x${height} is not supported for $mimeType")
    }
}
