package com.example.cutline.engine

import com.example.cutline.model.AudioKeyframe
import com.example.cutline.model.LayerTransform
import com.example.cutline.model.TransformKeyframe

object KeyframeInterpolator {
    fun transformAt(keyframes: List<TransformKeyframe>, timeMs: Long): LayerTransform? {
        if (keyframes.isEmpty()) return null
        val sorted = keyframes.sortedBy { it.timeMs }
        val before = sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()
        val after = sorted.firstOrNull { it.timeMs >= timeMs } ?: sorted.last()
        if (before == after || after.timeMs == before.timeMs) return before.transform
        val t = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs)).coerceIn(0f, 1f)
        return LayerTransform(
            scaleX = lerp(before.transform.scaleX, after.transform.scaleX, t),
            scaleY = lerp(before.transform.scaleY, after.transform.scaleY, t),
            rotationDegrees = lerp(before.transform.rotationDegrees, after.transform.rotationDegrees, t),
            positionX = lerp(before.transform.positionX, after.transform.positionX, t),
            positionY = lerp(before.transform.positionY, after.transform.positionY, t),
            opacity = lerp(before.transform.opacity, after.transform.opacity, t)
        )
    }

    fun volumeAt(keyframes: List<AudioKeyframe>, timeMs: Long): Float? {
        if (keyframes.isEmpty()) return null
        val sorted = keyframes.sortedBy { it.timeMs }
        val before = sorted.lastOrNull { it.timeMs <= timeMs } ?: sorted.first()
        val after = sorted.firstOrNull { it.timeMs >= timeMs } ?: sorted.last()
        if (before == after || after.timeMs == before.timeMs) return before.volume
        val t = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs)).coerceIn(0f, 1f)
        return lerp(before.volume, after.volume, t)
    }

    private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction
}
