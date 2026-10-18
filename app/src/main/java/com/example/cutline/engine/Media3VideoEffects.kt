package com.example.cutline.engine

import androidx.media3.common.Effect
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import com.example.cutline.model.FilterPreset
import com.example.cutline.model.ColorAdjustments
import com.example.cutline.model.LayerTransform

object Media3VideoEffects {
    fun forClip(
        filter: FilterPreset,
        transform: LayerTransform,
        color: ColorAdjustments = ColorAdjustments()
    ): List<Effect> = buildList {
        filterMatrix(filter)?.let(::add)
        colorMatrix(color)?.let(::add)
        if (transform.scaleX != 1f || transform.scaleY != 1f || transform.rotationDegrees != 0f) {
            add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(transform.scaleX, transform.scaleY)
                    .setRotationDegrees(transform.rotationDegrees)
                    .build()
            )
        }
    }

    private fun colorMatrix(value: ColorAdjustments): Effect? {
        if (value == ColorAdjustments()) return null
        val saturation = value.saturation.coerceIn(0f, 2f)
        val contrast = value.contrast.coerceIn(0f, 2f)
        val offset = value.brightness.coerceIn(-1f, 1f) + (1f - contrast) / 2f
        val r = .213f * (1f - saturation) + saturation
        val g = .715f * (1f - saturation)
        val b = .072f * (1f - saturation)
        val warm = value.temperature.coerceIn(-1f, 1f) * .12f
        return RgbMatrix { _, _ ->
            floatArrayOf(
                r * contrast, g * contrast, b * contrast, offset + warm,
                .213f * (1f - saturation) * contrast, (.715f * (1f - saturation) + saturation) * contrast, b * contrast, offset,
                (.213f * (1f - saturation)) * contrast, g * contrast, (b + saturation) * contrast, offset - warm,
                0f, 0f, 0f, 1f
            )
        }
    }

    private fun filterMatrix(filter: FilterPreset): Effect? = when (filter) {
        FilterPreset.NONE -> null
        FilterPreset.MONO -> RgbMatrix { _, _ ->
            floatArrayOf(
                .299f, .587f, .114f, 0f,
                .299f, .587f, .114f, 0f,
                .299f, .587f, .114f, 0f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.WARM -> RgbMatrix { _, _ ->
            floatArrayOf(
                1.08f, 0f, 0f, .02f,
                0f, 1.01f, 0f, 0f,
                0f, 0f, .88f, 0f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.COOL -> RgbMatrix { _, _ ->
            floatArrayOf(
                .9f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1.1f, .02f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.VIVID -> RgbMatrix { _, _ ->
            floatArrayOf(
                1.18f, -.08f, -.08f, 0f,
                -.08f, 1.18f, -.08f, 0f,
                -.08f, -.08f, 1.18f, 0f,
                0f, 0f, 0f, 1f
            )
        }
    }
}
