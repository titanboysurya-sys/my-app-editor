package com.example.cutline.export

import android.content.Context
import com.example.cutline.model.TimelineTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ExportHistoryEntry(
    val path: String,
    val createdAtMs: Long,
    val width: Int,
    val height: Int,
    val status: ExportStatus,
    val message: String? = null
)

data class ExportWarning(val message: String, val blocking: Boolean = false)

object ExportPreflight {
    fun check(tracks: List<TimelineTrack>, options: ExportOptions): List<ExportWarning> = buildList {
        val clips = tracks.flatMap { it.clips }
        if (clips.isEmpty()) add(ExportWarning("Timeline is empty", true))
        if (clips.none { !it.isStillImage && it.sourceEndMs > it.sourceStartMs }) {
            add(ExportWarning("No video media is available", true))
        }
        if (options.expectedWidth < 320 || options.expectedHeight < 320) {
            add(ExportWarning("Output resolution is unusually small"))
        }
        if (options.colorMode != ExportColorMode.SDR && options.videoMimeType == androidx.media3.common.MimeTypes.VIDEO_H264) {
            add(ExportWarning("HDR requires an HDR-capable HEVC/AV1 encoder", true))
        }
        if (clips.any { it.source.toString().startsWith("content:") && it.source.toString().isBlank() }) {
            add(ExportWarning("A media reference is missing", true))
        }
        if (clips.any { it.colorAdjustments.lutUri != null }) {
            add(ExportWarning("Imported LUTs will be applied during GPU export"))
        }
        if (clips.any { it.chromaKey.enabled }) {
            add(ExportWarning("Chroma key is enabled; export requires GPU effects support"))
        }
    }
}

class ExportHistoryStore(context: Context) {
    private val file = File(context.filesDir, "cutline-export-history.json")

    fun load(): List<ExportHistoryEntry> = runCatching {
        val array = JSONArray(file.takeIf { it.isFile }?.readText() ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val value = array.getJSONObject(index)
                add(ExportHistoryEntry(
                    path = value.getString("path"),
                    createdAtMs = value.getLong("createdAtMs"),
                    width = value.getInt("width"),
                    height = value.getInt("height"),
                    status = ExportStatus.valueOf(value.getString("status")),
                    message = value.optString("message").takeIf { it.isNotBlank() }
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun append(entry: ExportHistoryEntry) {
        val entries = (listOf(entry) + load()).take(20)
        file.writeText(JSONArray().apply {
            entries.forEach { value -> put(JSONObject().apply {
                put("path", value.path)
                put("createdAtMs", value.createdAtMs)
                put("width", value.width)
                put("height", value.height)
                put("status", value.status.name)
                value.message?.let { put("message", it) }
            }) }
        }.toString())
    }
}
