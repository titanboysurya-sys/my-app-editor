package com.example.cutline.model

data class StabilizationSettings(
    val enabled: Boolean = false,
    val strength: Float = .5f,
    val cropPercent: Float = 8f,
    val analysisReady: Boolean = false
)

data class FreezeFrameSettings(
    val enabled: Boolean = false,
    val atMs: Long = 0L,
    val durationMs: Long = 500L
)
