package com.example.cutline.model

import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor

enum class TrackKind { VIDEO, OVERLAY, AUDIO }

enum class BlendMode { NORMAL, ADD, MULTIPLY, SCREEN }

enum class FilterPreset { NONE, WARM, COOL, MONO, VIVID }

enum class TextAnimation { NONE, FADE_IN, TYPEWRITER, SLIDE, POP }

enum class TransitionKind { NONE, CROSSFADE, SLIDE, WIPE, ZOOM }

data class ColorAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val lutUri: Uri? = null
)

data class ChromaKeySettings(
    val enabled: Boolean = false,
    val keyColorArgb: Long = 0xFF00FF00,
    val threshold: Float = .35f,
    val softness: Float = .1f
)

data class StickerOverlay(
    val id: String,
    val assetUri: Uri,
    val startMs: Long,
    val endMs: Long,
    val transform: LayerTransform = LayerTransform()
)

data class TextStyle(
    val text: String,
    val fontFamily: String = "sans-serif",
    val fontSizeSp: Float = 32f,
    val colorArgb: Long = 0xFFFFFFFF,
    val strokeColorArgb: Long = 0xFF000000,
    val strokeWidth: Float = 0f,
    val backgroundArgb: Long? = null,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val animation: TextAnimation = TextAnimation.NONE,
    val karaoke: Boolean = false
)

enum class TextAlignment { START, CENTER, END }

data class AudioMix(
    val volume: Float = 1f,
    val pan: Float = 0f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

data class TransformKeyframe(
    val timeMs: Long,
    val transform: LayerTransform
)

data class AudioKeyframe(
    val timeMs: Long,
    val volume: Float
)

data class LayerTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val opacity: Float = 1f
)

data class TimelineClip(
    val id: String,
    val source: Uri,
    val timelineStartMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long,
    val sourceDurationMs: Long,
    val isStillImage: Boolean = false,
    val speed: Float = 1f,
    val filter: FilterPreset = FilterPreset.NONE,
    val colorAdjustments: ColorAdjustments = ColorAdjustments(),
    val chromaKey: ChromaKeySettings = ChromaKeySettings(),
    val transform: LayerTransform = LayerTransform(),
    val videoEffects: List<Effect> = emptyList(),
    val audioProcessors: List<AudioProcessor> = emptyList(),
    val mute: Boolean = false,
    val textStyle: TextStyle? = null,
    val audioMix: AudioMix = AudioMix(),
    val transitionIn: TransitionKind = TransitionKind.NONE,
    val transitionDurationMs: Long = 0L,
    val transformKeyframes: List<TransformKeyframe> = emptyList(),
    val audioKeyframes: List<AudioKeyframe> = emptyList()
) {
    val sourceTrimDurationMs get() = (sourceEndMs - sourceStartMs).coerceAtLeast(1L)
    val durationMs get() = (sourceTrimDurationMs / speed.coerceIn(.1f, 100f)).toLong().coerceAtLeast(1L)
    val timelineEndMs get() = timelineStartMs + durationMs
}

data class TimelineTrack(
    val id: String,
    val index: Int,
    val kind: TrackKind,
    val clips: List<TimelineClip>,
    val visible: Boolean = true,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val transform: LayerTransform = LayerTransform(),
    val blendMode: BlendMode = BlendMode.NORMAL
)
