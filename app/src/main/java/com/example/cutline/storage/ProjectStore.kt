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
                            put("filter", clip.filter.name)
                            put("brightness", clip.colorAdjustments.brightness.toDouble())
                            put("contrast", clip.colorAdjustments.contrast.toDouble())
                            put("saturation", clip.colorAdjustments.saturation.toDouble())
                            put("temperature", clip.colorAdjustments.temperature.toDouble())
                            clip.colorAdjustments.lutUri?.let { put("lutUri", it.toString()) }
                            put("chromaEnabled", clip.chromaKey.enabled)
                            put("chromaColor", clip.chromaKey.keyColorArgb)
                            put("chromaThreshold", clip.chromaKey.threshold.toDouble())
                            put("chromaSoftness", clip.chromaKey.softness.toDouble())
                            put("keyframes", JSONArray().apply {
                                clip.transformKeyframes.forEach { frame -> put(JSONObject().apply {
                                    put("timeMs", frame.timeMs)
                                    put("scaleX", frame.transform.scaleX.toDouble())
                                    put("scaleY", frame.transform.scaleY.toDouble())
                                    put("rotation", frame.transform.rotationDegrees.toDouble())
                                    put("x", frame.transform.positionX.toDouble())
                                    put("y", frame.transform.positionY.toDouble())
                                    put("opacity", frame.transform.opacity.toDouble())
                                    put("easing", frame.easing.name)
                                }) }
                            })
                            put("mute", clip.mute)
                            put("transition", clip.transitionIn.name)
                            put("transitionDurationMs", clip.transitionDurationMs)
                            put("mask", JSONObject().apply {
                                put("shape", clip.mask.shape.name); put("feather", clip.mask.feather)
                                put("invert", clip.mask.invert); put("x", clip.mask.x); put("y", clip.mask.y)
                                put("width", clip.mask.width); put("height", clip.mask.height)
                                put("rotation", clip.mask.rotationDegrees)
                            })
                            put("blur", JSONObject().apply {
                                put("enabled", clip.blur.enabled); put("radius", clip.blur.radius)
                                put("faceTracking", clip.blur.faceTracking)
                            })
                            put("motionTracking", JSONObject().apply {
                                put("enabled", clip.motionTracking.enabled)
                                put("targetLabel", clip.motionTracking.targetLabel)
                                put("confidence", clip.motionTracking.confidence)
                            })
                            put("audioEnhancement", JSONObject().apply {
                                put("equalizer", clip.audioEnhancement.equalizer)
                                put("bassDb", clip.audioEnhancement.bassDb); put("midDb", clip.audioEnhancement.midDb)
                                put("trebleDb", clip.audioEnhancement.trebleDb)
                                put("noiseReduction", clip.audioEnhancement.noiseReduction)
                                put("ducking", clip.audioEnhancement.ducking); put("beatSync", clip.audioEnhancement.beatSync)
                                put("normalize", clip.audioEnhancement.normalize)
                            })
                            put("speedCurve", JSONArray().apply { clip.speedCurve.forEach { point ->
                                put(JSONObject().put("timeMs", point.timeMs).put("speed", point.speed))
                            } })
                            put("captions", JSONArray().apply { clip.captions.forEach { cue ->
                                put(JSONObject().put("id", cue.id).put("startMs", cue.startMs).put("endMs", cue.endMs)
                                    .put("text", cue.text).put("languageTag", cue.languageTag))
                            } })
                            put("stabilization", JSONObject().apply {
                                put("enabled", clip.stabilization.enabled); put("strength", clip.stabilization.strength)
                                put("cropPercent", clip.stabilization.cropPercent); put("analysisReady", clip.stabilization.analysisReady)
                            })
                            put("freezeFrame", JSONObject().apply {
                                put("enabled", clip.freezeFrame.enabled); put("atMs", clip.freezeFrame.atMs)
                                put("durationMs", clip.freezeFrame.durationMs)
                            })
                            put("stabilizationKeyframes", JSONArray().apply {
                                clip.stabilizationKeyframes.forEach { frame -> put(JSONObject().apply {
                                    put("timeMs", frame.timeMs); put("scaleX", frame.transform.scaleX)
                                    put("scaleY", frame.transform.scaleY); put("rotation", frame.transform.rotationDegrees)
                                    put("x", frame.transform.positionX); put("y", frame.transform.positionY)
                                }) }
                            })
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

    /** Copies the current project document to a user-selected backup/archive location. */
    fun exportBackup(destination: File): File {
        require(file.isFile) { "No saved project is available" }
        destination.parentFile?.mkdirs()
        file.copyTo(destination, overwrite = true)
        return destination
    }

    /** Restores a validated JSON project atomically; the caller can then call load(). */
    fun restoreBackup(source: File): List<TimelineTrack> {
        require(source.isFile) { "Backup file does not exist" }
        val parsed = runCatching {
            JSONObject(source.readText()).optJSONArray("tracks") ?: error("Missing tracks")
        }.getOrElse { error("Invalid Cutline project backup: ${it.message}") }
        val temporary = File(context.filesDir, "cutline-project.restore.tmp")
        source.copyTo(temporary, overwrite = true)
        temporary.copyTo(file, overwrite = true)
        temporary.delete()
        return load().also { require(it.size == parsed.length()) { "Project backup could not be restored" } }
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
                            val keyframes = clip.optJSONArray("keyframes")
                            add(TimelineClip(
                                id = clip.getString("id"),
                                source = Uri.parse(clip.getString("source")),
                                timelineStartMs = clip.getLong("timelineStartMs"),
                                sourceStartMs = clip.optLong("sourceStartMs"),
                                sourceEndMs = clip.getLong("sourceEndMs"),
                                sourceDurationMs = clip.getLong("sourceDurationMs"),
                                isStillImage = clip.optBoolean("still", false),
                                speed = clip.optDouble("speed", 1.0).toFloat(),
                                filter = runCatching { com.example.cutline.model.FilterPreset.valueOf(clip.optString("filter")) }.getOrDefault(com.example.cutline.model.FilterPreset.NONE),
                                colorAdjustments = com.example.cutline.model.ColorAdjustments(
                                    brightness = clip.optDouble("brightness", 0.0).toFloat(),
                                    contrast = clip.optDouble("contrast", 1.0).toFloat(),
                                    saturation = clip.optDouble("saturation", 1.0).toFloat(),
                                    temperature = clip.optDouble("temperature", 0.0).toFloat()
                                    , lutUri = clip.optString("lutUri").takeIf { it.isNotBlank() }?.let(Uri::parse)
                                ),
                                chromaKey = com.example.cutline.model.ChromaKeySettings(
                                    enabled = clip.optBoolean("chromaEnabled", false),
                                    keyColorArgb = clip.optLong("chromaColor", 0xFF00FF00),
                                    threshold = clip.optDouble("chromaThreshold", .35).toFloat(),
                                    softness = clip.optDouble("chromaSoftness", .1).toFloat()
                                ),
                                transformKeyframes = buildList {
                                    if (keyframes != null) for (k in 0 until keyframes.length()) {
                                        val frame = keyframes.getJSONObject(k)
                                        add(com.example.cutline.model.TransformKeyframe(
                                            timeMs = frame.optLong("timeMs"),
                                            transform = com.example.cutline.model.LayerTransform(
                                                scaleX = frame.optDouble("scaleX", 1.0).toFloat(),
                                                scaleY = frame.optDouble("scaleY", 1.0).toFloat(),
                                                rotationDegrees = frame.optDouble("rotation", 0.0).toFloat(),
                                                positionX = frame.optDouble("x", 0.0).toFloat(),
                                                positionY = frame.optDouble("y", 0.0).toFloat(),
                                                opacity = frame.optDouble("opacity", 1.0).toFloat()
                                            ),
                                            easing = runCatching { com.example.cutline.model.KeyframeEasing.valueOf(frame.optString("easing")) }.getOrDefault(com.example.cutline.model.KeyframeEasing.LINEAR)
                                        ))
                                    }
                                },
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
                                transitionDurationMs = clip.optLong("transitionDurationMs", 0L),
                                mask = clip.optJSONObject("mask")?.let { m -> com.example.cutline.model.MaskSettings(
                                    shape = runCatching { com.example.cutline.model.MaskShape.valueOf(m.optString("shape")) }.getOrDefault(com.example.cutline.model.MaskShape.NONE),
                                    feather = m.optDouble("feather", 0.0).toFloat(), invert = m.optBoolean("invert"),
                                    x = m.optDouble("x", 0.0).toFloat(), y = m.optDouble("y", 0.0).toFloat(),
                                    width = m.optDouble("width", 1.0).toFloat(), height = m.optDouble("height", 1.0).toFloat(),
                                    rotationDegrees = m.optDouble("rotation", 0.0).toFloat()
                                ) } ?: com.example.cutline.model.MaskSettings(),
                                blur = clip.optJSONObject("blur")?.let { b -> com.example.cutline.model.BlurSettings(
                                    b.optBoolean("enabled"), b.optDouble("radius", 0.0).toFloat(), b.optBoolean("faceTracking")
                                ) } ?: com.example.cutline.model.BlurSettings(),
                                motionTracking = clip.optJSONObject("motionTracking")?.let { m -> com.example.cutline.model.MotionTrackingSettings(
                                    m.optBoolean("enabled"), m.optString("targetLabel", "object"), m.optDouble("confidence", .5).toFloat()
                                ) } ?: com.example.cutline.model.MotionTrackingSettings(),
                                audioEnhancement = clip.optJSONObject("audioEnhancement")?.let { a -> com.example.cutline.model.AudioEnhancementSettings(
                                    a.optBoolean("equalizer"), a.optDouble("bassDb").toFloat(), a.optDouble("midDb").toFloat(),
                                    a.optDouble("trebleDb").toFloat(), a.optDouble("noiseReduction").toFloat(),
                                    a.optBoolean("ducking"), a.optBoolean("beatSync"), a.optBoolean("normalize")
                                ) } ?: com.example.cutline.model.AudioEnhancementSettings(),
                                speedCurve = buildList { clip.optJSONArray("speedCurve")?.let { points ->
                                    for (p in 0 until points.length()) points.getJSONObject(p).let { add(com.example.cutline.model.SpeedPoint(it.optLong("timeMs"), it.optDouble("speed", 1.0).toFloat())) }
                                } },
                                captions = buildList { clip.optJSONArray("captions")?.let { cues ->
                                    for (c in 0 until cues.length()) cues.getJSONObject(c).let { add(com.example.cutline.model.CaptionCue(
                                        it.optString("id"), it.optLong("startMs"), it.optLong("endMs"), it.optString("text"), it.optString("languageTag", "en-US")
                                    )) }
                                } },
                                stabilization = clip.optJSONObject("stabilization")?.let { s -> com.example.cutline.model.StabilizationSettings(
                                    s.optBoolean("enabled"), s.optDouble("strength", .5).toFloat(), s.optDouble("cropPercent", 8.0).toFloat(), s.optBoolean("analysisReady")
                                ) } ?: com.example.cutline.model.StabilizationSettings(),
                                freezeFrame = clip.optJSONObject("freezeFrame")?.let { f -> com.example.cutline.model.FreezeFrameSettings(
                                    f.optBoolean("enabled"), f.optLong("atMs"), f.optLong("durationMs", 500L)
                                ) } ?: com.example.cutline.model.FreezeFrameSettings(),
                                stabilizationKeyframes = buildList {
                                    clip.optJSONArray("stabilizationKeyframes")?.let { frames ->
                                        for (frameIndex in 0 until frames.length()) {
                                            val frame = frames.getJSONObject(frameIndex)
                                            add(com.example.cutline.model.TransformKeyframe(
                                                timeMs = frame.optLong("timeMs"),
                                                transform = com.example.cutline.model.LayerTransform(
                                                    scaleX = frame.optDouble("scaleX", 1.0).toFloat(),
                                                    scaleY = frame.optDouble("scaleY", 1.0).toFloat(),
                                                    rotationDegrees = frame.optDouble("rotation", 0.0).toFloat(),
                                                    positionX = frame.optDouble("x", 0.0).toFloat(),
                                                    positionY = frame.optDouble("y", 0.0).toFloat()
                                                )
                                            ))
                                        }
                                    }
                                }
                            ))
                        }
                    }
                ))
            }
        }
    }
}
