package com.example.cutline.engine

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TimelineTrack
import com.example.cutline.model.TrackKind

@OptIn(UnstableApi::class)
data class CompositionOptions(
    val outputWidth: Int = 1080,
    val outputHeight: Int = 1920,
    val preserveHdr: Boolean = false,
    val allowAudioTransmux: Boolean = true
)

@OptIn(UnstableApi::class)
data class BuiltComposition(
    val composition: Composition,
    val durationMs: Long
)

@OptIn(UnstableApi::class)
object TimelineCompositionConverter {
    fun build(
        tracks: List<TimelineTrack>,
        options: CompositionOptions = CompositionOptions()
    ): BuiltComposition {
        val ordered = tracks.sortedBy { it.index }
        val soloIds = ordered.filter { it.solo }.map { it.id }.toSet()
        val visuals = ordered
            .filter { it.kind == TrackKind.VIDEO || it.kind == TrackKind.OVERLAY }
            .filter { it.visible && it.clips.isNotEmpty() }
            .sortedByDescending { it.index }
        val audios = ordered
            .filter { it.kind == TrackKind.AUDIO && it.visible && !it.mute }
            .filter { soloIds.isEmpty() || it.id in soloIds }

        val visualSequences = visuals.map {
            buildSequence(it, removeVideo = false, options = options)
        }
        val audioSequences = audios.map {
            buildSequence(it, removeVideo = true, options = options)
        }
        val sequences = visualSequences + audioSequences
        require(sequences.isNotEmpty()) { "Timeline contains no media" }

        val builder = Composition.Builder(sequences)
            .setTransmuxAudio(
                options.allowAudioTransmux &&
                    audioSequences.isEmpty() &&
                    visualSequences.size == 1
            )

        if (visualSequences.size > 1) {
            builder.setVideoCompositorSettings(
                CutlineCompositorSettings(
                    width = options.outputWidth,
                    height = options.outputHeight,
                    layers = visuals.mapIndexed { inputId, track ->
                        CompositorLayer(inputId, track.transform, track.blendMode)
                    }
                )
            )
        }
        if (options.preserveHdr) {
            builder.setHdrMode(Composition.HDR_MODE_KEEP_HDR)
        }

        return BuiltComposition(
            composition = builder.build(),
            durationMs = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndMs } ?: 0L
        )
    }

    private fun buildSequence(
        track: TimelineTrack,
        removeVideo: Boolean,
        options: CompositionOptions
    ): EditedMediaItemSequence {
        val mediaTypes = if (removeVideo) {
            setOf(C.TRACK_TYPE_AUDIO)
        } else {
            setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
        }
        val builder = EditedMediaItemSequence.Builder(mediaTypes)
        var cursorMs = 0L

        track.clips.sortedBy { it.timelineStartMs }.forEach { clip ->
            if (clip.timelineStartMs > cursorMs) {
                builder.addGap(us(clip.timelineStartMs - cursorMs))
            }

            builder.addItem(
                buildItem(
                    clip = clip,
                    removeVideo = removeVideo,
                    muteAudio = removeVideo.not() && (track.mute || clip.mute),
                    options = options
                )
            )
            cursorMs = maxOf(cursorMs, clip.timelineEndMs)
        }
        return builder.build()
    }

    private fun buildItem(
        clip: TimelineClip,
        removeVideo: Boolean,
        muteAudio: Boolean,
        options: CompositionOptions
    ): EditedMediaItem {
        val mediaBuilder = MediaItem.Builder().setUri(clip.source)
        if (clip.isStillImage) {
            mediaBuilder.setImageDurationMs(clip.durationMs)
        } else {
            mediaBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceStartMs)
                    .setEndPositionMs(clip.sourceEndMs)
                    .build()
            )
        }
        val mediaItem = mediaBuilder.build()

        val audio = if (muteAudio) emptyList() else clip.audioProcessors
        val video: List<Effect> = if (removeVideo) {
            emptyList()
        } else {
            clip.videoEffects + Media3VideoEffects.forClip(
                clip.filter,
                clip.transform,
                clip.colorAdjustments
            ) +
                textEffect(clip) +
                Presentation.createForWidthAndHeight(
                options.outputWidth,
                options.outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        }
        val item = EditedMediaItem.Builder(mediaItem)
            .setDurationUs(us(clip.sourceTrimDurationMs))
            .setEffects(Effects(audio, video))

        if (removeVideo) item.setRemoveVideo(true)
        if (clip.speed != 1f) {
            val speed = clip.speed.coerceIn(.1f, 100f)
            item.setSpeed(object : SpeedProvider {
                override fun getSpeed(presentationTimeUs: Long) = speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long) = C.TIME_UNSET
            })
        }
        return item.build()
    }

    private fun textEffect(clip: TimelineClip): List<Effect> {
        val style = clip.textStyle ?: return emptyList()
        val text = SpannableString(style.text).apply {
            setSpan(
                ForegroundColorSpan(style.colorArgb.toInt()),
                0,
                length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return listOf(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(text))))
    }

    private fun us(ms: Long) = ms.coerceAtLeast(0L) * 1_000L
}
