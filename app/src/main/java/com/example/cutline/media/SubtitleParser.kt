package com.example.cutline.media

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

object SubtitleParser {
    fun parse(contents: String): List<SubtitleCue> {
        val lines = contents.replace("\r", "").lines()
        val cues = mutableListOf<SubtitleCue>()
        var index = 0
        while (index < lines.size) {
            if (lines[index].trim().equals("WEBVTT", true) || lines[index].isBlank()) {
                index++
                continue
            }
            if (index + 1 < lines.size && !lines[index].contains("-->")) index++
            if (index >= lines.size || !lines[index].contains("-->")) continue
            val times = lines[index].split("-->")
            val start = parseTime(times[0].trim())
            val end = parseTime(times[1].trim().substringBefore(' '))
            index++
            val text = buildList {
                while (index < lines.size && lines[index].isNotBlank()) add(lines[index++])
            }.joinToString("\n")
            if (text.isNotBlank()) cues += SubtitleCue(start, end, text)
            index++
        }
        return cues
    }

    fun toSrt(cues: List<SubtitleCue>): String = cues.mapIndexed { index, cue ->
        "${index + 1}\n${formatTime(cue.startMs)} --> ${formatTime(cue.endMs)}\n${cue.text}\n"
    }.joinToString("\n")

    private fun parseTime(value: String): Long {
        val parts = value.replace(',', '.').split(':')
        val seconds = parts.last().toDouble()
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
        val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
        return ((hours * 3600 + minutes * 60 + seconds) * 1000).toLong()
    }

    private fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        return "%02d:%02d:%02d,%03d".format(
            total / 3_600_000, total / 60_000 % 60, total / 1_000 % 60, total % 1_000
        )
    }
}
