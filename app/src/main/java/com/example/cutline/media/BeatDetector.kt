package com.example.cutline.media

import kotlin.math.abs

data class BeatMarker(val timeMs: Long, val strength: Float)

/** Lightweight offline onset detector. Feed decoded mono PCM frames from MediaExtractor/AudioTrack. */
object BeatDetector {
    fun detect(samples: FloatArray, sampleRate: Int, windowSize: Int = 1024): List<BeatMarker> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()
        val energies = mutableListOf<Float>()
        var offset = 0
        while (offset < samples.size) {
            val end = (offset + windowSize).coerceAtMost(samples.size)
            var energy = 0f
            for (i in offset until end) energy += abs(samples[i])
            energies += energy / (end - offset).coerceAtLeast(1)
            offset += windowSize
        }
        val average = energies.average().toFloat().coerceAtLeast(.0001f)
        return energies.mapIndexedNotNull { index, energy ->
            val strength = (energy / average - 1f).coerceIn(0f, 1f)
            if (strength > .28f) BeatMarker(index * windowSize * 1000L / sampleRate, strength) else null
        }
    }
}
