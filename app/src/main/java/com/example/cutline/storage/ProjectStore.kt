package com.example.cutline.storage

import android.content.Context
import android.net.Uri
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TimelineTrack
import com.example.cutline.model.TrackKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small local JSON project store; media remains referenced by persisted Uri strings. */
class ProjectStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "cutline-project.json")

    fun save(tracks: List<TimelineTrack>) {
        val root = JSONObject().put("tracks", JSONArray().apply {
            tracks.forEach { track ->
                put(JSONObject().apply {
                    put("id", track.id)
                    put("index", track.index)
                    put("kind", track.kind.name)
                    put("mute", track.mute)
                    put("visible", track.visible)
                    put("clips", JSONArray().apply {
                        track.clips.forEach { clip -> put(JSONObject().apply {
                            put("id", clip.id)
                            put("source", clip.source.toString())
                            put("timelineStartMs", clip.timelineStartMs)
                            put("sourceStartMs", clip.sourceStartMs)
                            put("sourceEndMs", clip.sourceEndMs)
                            put("sourceDurationMs", clip.sourceDurationMs)
                            put("still", clip.isStillImage)
                            put("speed", clip.speed.toDouble())
                            put("mute", clip.mute)
                            put("transition", clip.transitionIn.name)
                            put("transitionDurationMs", clip.transitionDurationMs)
                            put("audioVolume", clip.audioMix.volume.toDouble())
                            put("audioPan", clip.audioMix.pan.toDouble())
                            put("fadeInMs", clip.audioMix.fadeInMs)
                            put("fadeOutMs", clip.audioMix.fadeOutMs)
                            clip.textStyle?.let { style -> put("text", JSONObject().apply {
                                put("value", style.text)
                                put("fontFamily", style.fontFamily)
                                put("fontSizeSp", style.fontSizeSp.toDouble())
                                put("color", style.colorArgb)
                                put("strokeColor", style.strokeColorArgb)
                                put("strokeWidth", style.strokeWidth.toDouble())
                                put("background", style.backgroundArgb)
                                put("alignment", style.alignment.name)
                                put("animation", style.animation.name)
                                put("karaoke", style.karaoke)
                            }) }
                        }) }
                    })
                })
            }
        })
        file.writeText(root.toString())
    }

    fun load(): List<TimelineTrack> {
        if (!file.isFile) return emptyList()
        val tracks = JSONObject(file.readText()).optJSONArray("tracks") ?: return emptyList()
        return buildList {
            for (i in 0 until tracks.length()) {
                val value = tracks.getJSONObject(i)
                val clips = value.optJSONArray("clips") ?: JSONArray()
                add(TimelineTrack(
                    id = value.getString("id"),
                    index = value.getInt("index"),
                    kind = TrackKind.valueOf(value.getString("kind")),
                    visible = value.optBoolean("visible", true),
                    mute = value.optBoolean("mute", false),
                    clips = buildList {
                        for (j in 0 until clips.length()) {
                            val clip = clips.getJSONObject(j)
                            val text = clip.optJSONObject("text")
                            add(TimelineClip(
                                id = clip.getString("id"),
                                source = Uri.parse(clip.getString("source")),
                                timelineStartMs = clip.getLong("timelineStartMs"),
                                sourceStartMs = clip.optLong("sourceStartMs"),
                                sourceEndMs = clip.getLong("sourceEndMs"),
                                sourceDurationMs = clip.getLong("sourceDurationMs"),
                                isStillImage = clip.optBoolean("still", false),
                                speed = clip.optDouble("speed", 1.0).toFloat(),
                                mute = clip.optBoolean("mute", false),
                                textStyle = text?.let {
                                    com.example.cutline.model.TextStyle(
                                        text = it.optString("value"),
                                        fontFamily = it.optString("fontFamily", "sans-serif"),
                                        fontSizeSp = it.optDouble("fontSizeSp", 32.0).toFloat(),
                                        colorArgb = it.optLong("color", 0xFFFFFFFF),
                                        strokeColorArgb = it.optLong("strokeColor", 0xFF000000),
                                        strokeWidth = it.optDouble("strokeWidth", 0.0).toFloat(),
                                        backgroundArgb = if (it.has("background") && !it.isNull("background")) it.optLong("background") else null,
                                        alignment = runCatching { com.example.cutline.model.TextAlignment.valueOf(it.optString("alignment")) }.getOrDefault(com.example.cutline.model.TextAlignment.CENTER),
                                        animation = runCatching { com.example.cutline.model.TextAnimation.valueOf(it.optString("animation")) }.getOrDefault(com.example.cutline.model.TextAnimation.NONE),
                                        karaoke = it.optBoolean("karaoke", false)
                                    )
                                },
                                audioMix = com.example.cutline.model.AudioMix(
                                    volume = clip.optDouble("audioVolume", 1.0).toFloat(),
                                    pan = clip.optDouble("audioPan", 0.0).toFloat(),
                                    fadeInMs = clip.optLong("fadeInMs", 0L),
                                    fadeOutMs = clip.optLong("fadeOutMs", 0L)
                                ),
                                transitionIn = runCatching { com.example.cutline.model.TransitionKind.valueOf(clip.optString("transition")) }.getOrDefault(com.example.cutline.model.TransitionKind.NONE),
                                transitionDurationMs = clip.optLong("transitionDurationMs", 0L)
                            ))
                        }
                    }
                ))
            }
        }
    }
}
