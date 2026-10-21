package com.example.cutline.model

/** Render-neutral settings for features that can be previewed and exported offline. */
enum class MaskShape { NONE, RECTANGLE, ELLIPSE, ROUNDED_RECTANGLE, LINEAR_GRADIENT }

data class MaskSettings(
    val shape: MaskShape = MaskShape.NONE,
    val feather: Float = 0f,
    val invert: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotationDegrees: Float = 0f
)

data class BlurSettings(
    val enabled: Boolean = false,
    val radius: Float = 0f,
    val faceTracking: Boolean = false
)

data class MotionTrackingSettings(
    val enabled: Boolean = false,
    val targetLabel: String = "object",
    val confidence: Float = .5f
)

data class AudioEnhancementSettings(
    val equalizer: Boolean = false,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val noiseReduction: Float = 0f,
    val ducking: Boolean = false,
    val beatSync: Boolean = false,
    val normalize: Boolean = false
)

data class SpeedPoint(val timeMs: Long, val speed: Float)

object SpeedCurveMath {
    fun speedAt(points: List<SpeedPoint>, timeMs: Long, fallback: Float = 1f): Float {
        if (points.isEmpty()) return fallback.coerceIn(.1f, 100f)
        val sorted = points.sortedBy { it.timeMs }
        val before = sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()
        val after = sorted.firstOrNull { it.timeMs >= timeMs } ?: sorted.last()
        if (before == after || after.timeMs <= before.timeMs) return before.speed.coerceIn(.1f, 100f)
        val fraction = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs)).coerceIn(0f, 1f)
        return (before.speed + (after.speed - before.speed) * fraction).coerceIn(.1f, 100f)
    }

    fun outputDurationMs(sourceDurationMs: Long, points: List<SpeedPoint>, fallback: Float = 1f): Long {
        if (sourceDurationMs <= 0L) return 0L
        if (points.isEmpty()) return (sourceDurationMs / fallback.coerceIn(.1f, 100f)).toLong().coerceAtLeast(1L)
        val samples = (sourceDurationMs / 20L).coerceIn(1L, 20_000L).toInt()
        val step = sourceDurationMs.toDouble() / samples
        var output = 0.0
        repeat(samples) { index ->
            val midpoint = ((index + .5) * step).toLong()
            output += step / speedAt(points, midpoint, fallback)
        }
        return output.toLong().coerceAtLeast(1L)
    }

    fun sourceOffsetAtOutput(outputMs: Long, sourceDurationMs: Long, points: List<SpeedPoint>, fallback: Float = 1f): Long {
        var low = 0L
        var high = sourceDurationMs.coerceAtLeast(0L)
        repeat(32) {
            val middle = (low + high) / 2L
            if (outputDurationMs(middle, points, fallback) < outputMs) low = middle + 1L else high = middle
        }
        return low.coerceIn(0L, sourceDurationMs)
    }
}

data class CaptionCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val languageTag: String = "en-US"
)
