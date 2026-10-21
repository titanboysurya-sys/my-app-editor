package com.example.cutline.media

import com.example.cutline.model.TimelineTrack
import org.json.JSONArray
import org.json.JSONObject

/** Portable edit-decision export for moving a cut between editors. */
object TimelineInterchange {
    fun toEditDecisionJson(tracks: List<TimelineTrack>): String {
        val root = JSONObject().put("schema", "com.cutline.edit-decision").put("version", 1)
        val trackArray = JSONArray()
        tracks.forEach { track ->
            val clipArray = JSONArray()
            track.clips.forEach { clip ->
                clipArray.put(JSONObject()
                    .put("id", clip.id).put("source", clip.source.toString())
                    .put("timelineStartMs", clip.timelineStartMs)
                    .put("sourceStartMs", clip.sourceStartMs)
                    .put("sourceEndMs", clip.sourceEndMs)
                    .put("speed", clip.speed.toDouble()))
            }
            trackArray.put(JSONObject().put("id", track.id).put("kind", track.kind.name)
                .put("index", track.index).put("clips", clipArray))
        }
        root.put("tracks", trackArray)
        return root.toString(2)
    }

    /** CMX 3600-style human-readable EDL; source media remains linked by reel name. */
    fun toEdl(tracks: List<TimelineTrack>, frameRate: Int = 30): String = buildString {
        var event = 1
        tracks.filter { it.kind != com.example.cutline.model.TrackKind.OVERLAY }.forEach { track ->
            track.clips.forEach { clip ->
                val reel = clip.source.lastPathSegment.orEmpty().take(8).uppercase().ifBlank { "AX" }
                val srcIn = timecode(clip.sourceStartMs, frameRate)
                val srcOut = timecode(clip.sourceEndMs, frameRate)
                val recIn = timecode(clip.timelineStartMs, frameRate)
                val recOut = timecode(clip.timelineEndMs, frameRate)
                appendLine("%03d  %-8s V     C        %s %s %s %s".format(event++, reel, srcIn, srcOut, recIn, recOut))
                appendLine("* FROM CLIP ${clip.id}")
                appendLine("* SOURCE ${clip.source}")
            }
        }
    }

    private fun timecode(ms: Long, fps: Int): String {
        val frames = (ms.coerceAtLeast(0L) * fps / 1000L)
        val totalSeconds = frames / fps
        return "%02d:%02d:%02d:%02d".format(totalSeconds / 3600, totalSeconds / 60 % 60, totalSeconds % 60, frames % fps)
    }
}
