package com.example.cutline.engine

import com.example.cutline.model.AudioMix
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TransitionKind
import kotlin.math.max
import kotlin.math.min

/** Render-neutral plans keep editing decisions independent from Media3 and easy to test. */
data class TransitionPlan(
    val kind: TransitionKind,
    val durationMs: Long,
    val outgoingClipId: String,
    val incomingClipId: String
)

object TransitionPlanner {
    fun between(outgoing: TimelineClip, incoming: TimelineClip): TransitionPlan? {
        if (incoming.transitionIn == TransitionKind.NONE) return null
        val overlap = min(
            incoming.transitionDurationMs,
            max(0L, outgoing.timelineEndMs - incoming.timelineStartMs)
        )
        if (overlap <= 0L) return null
        return TransitionPlan(incoming.transitionIn, overlap, outgoing.id, incoming.id)
    }
}

object AudioMixCurve {
    fun gainAt(positionMs: Long, durationMs: Long, mix: AudioMix): Float {
        val fadeIn = if (mix.fadeInMs == 0L) 1f else (positionMs / mix.fadeInMs.toFloat()).coerceIn(0f, 1f)
        val remaining = durationMs - positionMs
        val fadeOut = if (mix.fadeOutMs == 0L) 1f else (remaining / mix.fadeOutMs.toFloat()).coerceIn(0f, 1f)
        return mix.volume.coerceIn(0f, 2f) * min(fadeIn, fadeOut)
    }

    fun panAt(positionMs: Long, mix: AudioMix): Float = mix.pan.coerceIn(-1f, 1f)
}
