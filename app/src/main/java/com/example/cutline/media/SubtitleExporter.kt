package com.example.cutline.media

/** Export formats supported without an FFmpeg dependency. */
object SubtitleExporter {
    fun toVtt(cues: List<SubtitleCue>): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        cues.forEachIndexed { index, cue ->
            appendLine("${index + 1}")
            appendLine("${format(cue.startMs, '.') } --> ${format(cue.endMs, '.')}")
            appendLine(cue.text)
            appendLine()
        }
    }

    fun toAss(cues: List<SubtitleCue>): String = buildString {
        appendLine("[Script Info]")
        appendLine("ScriptType: v4.00+")
        appendLine("[V4+ Styles]")
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Alignment")
        appendLine("Style: Default,Arial,48,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,0,0,2")
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Text")
        cues.forEach { cue -> appendLine("Dialogue: 0,${format(cue.startMs, '.')},${format(cue.endMs, '.')},Default,${cue.text.replace("\n", "\\N")}") }
    }

    private fun format(ms: Long, separator: Char): String = "%d:%02d:%02d.%02d".format(
        ms / 3_600_000, ms / 60_000 % 60, ms / 1_000 % 60, ms % 1_000 / 10
    ).replace('.', separator)
}
