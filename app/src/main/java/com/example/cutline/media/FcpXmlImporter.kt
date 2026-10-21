package com.example.cutline.media

import android.net.Uri
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TimelineTrack
import com.example.cutline.model.TrackKind
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Imports the common FCPXML asset-clip subset without mutating the current project. */
object FcpXmlImporter {
    fun import(file: File): List<TimelineTrack> {
        require(file.isFile) { "FCPXML file does not exist" }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            .newDocumentBuilder().parse(file)
        val assets = buildMap {
            val nodes = document.getElementsByTagName("asset")
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val id = node.attributes?.getNamedItem("id")?.nodeValue ?: continue
                val src = node.attributes?.getNamedItem("src")?.nodeValue ?: continue
                put(id, Uri.parse(src))
            }
        }
        val clips = mutableListOf<TimelineClip>()
        val nodes = document.getElementsByTagName("asset-clip")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val ref = node.attributes?.getNamedItem("ref")?.nodeValue ?: continue
            val source = assets[ref] ?: continue
            val start = parseTime(node.attributes?.getNamedItem("start")?.nodeValue)
            val duration = parseTime(node.attributes?.getNamedItem("duration")?.nodeValue)
            if (duration <= 0L) continue
            val sourceStart = parseTime(node.attributes?.getNamedItem("offset")?.nodeValue)
            clips += TimelineClip(
                id = "fcpxml-$index",
                source = source,
                timelineStartMs = start,
                sourceStartMs = sourceStart,
                sourceEndMs = sourceStart + duration,
                sourceDurationMs = sourceStart + duration
            )
        }
        return listOf(TimelineTrack("fcpxml-video", 0, TrackKind.VIDEO, clips.sortedBy { it.timelineStartMs }))
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val raw = value.removeSuffix("s")
        val parts = raw.split('/')
        val seconds = if (parts.size == 2) parts[0].toDouble() / parts[1].toDouble() else raw.toDoubleOrNull() ?: 0.0
        return (seconds * 1000.0).toLong().coerceAtLeast(0L)
    }
}
