package com.example.cutline.engine

import android.content.Context
import android.graphics.Color
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import com.example.cutline.model.FilterPreset
import com.example.cutline.model.ColorAdjustments
import com.example.cutline.model.LayerTransform
import com.example.cutline.model.TransformKeyframe
import com.example.cutline.model.ChromaKeySettings
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(UnstableApi::class)
object Media3VideoEffects {
    fun forClip(
        filter: FilterPreset,
        transform: LayerTransform,
        color: ColorAdjustments = ColorAdjustments(),
        keyframes: List<TransformKeyframe> = emptyList(),
        stabilizationKeyframes: List<TransformKeyframe> = emptyList(),
        context: Context? = null,
        chromaKey: ChromaKeySettings = ChromaKeySettings()
    ): List<Effect> = buildList {
        filterMatrix(filter)?.let(::add)
        colorMatrix(color)?.let(::add)
        if (context != null && color.lutUri != null) {
            lutEffect(context, color.lutUri)?.let(::add)
        }
        if (chromaKey.enabled) add(ChromaKeyEffect(chromaKey))
        if (transform.scaleX != 1f || transform.scaleY != 1f || transform.rotationDegrees != 0f) {
            add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(transform.scaleX, transform.scaleY)
                    .setRotationDegrees(transform.rotationDegrees)
                    .build()
            )
        }
        if (keyframes.isNotEmpty() || stabilizationKeyframes.isNotEmpty()) {
            add(KeyframeMatrixEffect(keyframes, stabilizationKeyframes, transform))
        }
    }

    private fun lutEffect(context: Context, uri: android.net.Uri): Effect? = runCatching {
        val lines = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readLines()
        } ?: return null
        val size = lines.firstNotNullOfOrNull { line ->
            line.trim().takeIf { it.startsWith("LUT_3D_SIZE", true) }
                ?.split(Regex("\\s+"))?.lastOrNull()?.toIntOrNull()
        } ?: return null
        require(size in 2..64) { "Unsupported LUT size" }
        val values = lines.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size == 3 && parts.all { it.toFloatOrNull() != null }) parts.map(String::toFloat) else null
        }
        require(values.size >= size * size * size) { "Incomplete .cube LUT" }
        val cube = Array(size) { Array(size) { IntArray(size) } }
        var index = 0
        for (r in 0 until size) for (g in 0 until size) for (b in 0 until size) {
            val value = values[index++]
            cube[r][g][b] = Color.rgb(
                (value[0].coerceIn(0f, 1f) * 255f).toInt(),
                (value[1].coerceIn(0f, 1f) * 255f).toInt(),
                (value[2].coerceIn(0f, 1f) * 255f).toInt()
            )
        }
        SingleColorLut.createFromCube(cube)
    }.getOrNull()

    private class KeyframeMatrixEffect(
        private val keyframes: List<TransformKeyframe>,
        private val stabilizationKeyframes: List<TransformKeyframe>,
        private val fallback: LayerTransform
    ) : androidx.media3.effect.MatrixTransformation {
        override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
            val timeMs = presentationTimeUs / 1000L
            val user = KeyframeInterpolator.transformAt(keyframes, timeMs) ?: fallback
            val stabilization = KeyframeInterpolator.transformAt(stabilizationKeyframes, timeMs) ?: LayerTransform()
            val value = LayerTransform(
                scaleX = user.scaleX * stabilization.scaleX,
                scaleY = user.scaleY * stabilization.scaleY,
                rotationDegrees = user.rotationDegrees + stabilization.rotationDegrees,
                positionX = user.positionX + stabilization.positionX,
                positionY = user.positionY + stabilization.positionY,
                opacity = user.opacity * stabilization.opacity
            )
            return android.graphics.Matrix().apply {
                setScale(value.scaleX, value.scaleY)
                postRotate(value.rotationDegrees)
                postTranslate(value.positionX, value.positionY)
            }
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
        FilterPreset.CINEMATIC -> RgbMatrix { _, _ ->
            floatArrayOf(
                1.08f, -.04f, -.04f, .01f,
                -.02f, 1.02f, 0f, 0f,
                0f, .02f, .92f, .01f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.SEPIA -> RgbMatrix { _, _ ->
            floatArrayOf(
                .393f, .769f, .189f, 0f,
                .349f, .686f, .168f, 0f,
                .272f, .534f, .131f, 0f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.NOIR -> RgbMatrix { _, _ ->
            floatArrayOf(
                .45f, .45f, .10f, -.05f,
                .45f, .45f, .10f, -.05f,
                .45f, .45f, .10f, -.05f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.TEAL_ORANGE -> RgbMatrix { _, _ ->
            floatArrayOf(
                1.12f, -.08f, -.04f, .01f,
                .02f, 1.02f, .02f, 0f,
                -.04f, .04f, 1.10f, .01f,
                0f, 0f, 0f, 1f
            )
        }
        FilterPreset.FADE -> RgbMatrix { _, _ ->
            floatArrayOf(
                .86f, .06f, .06f, .08f,
                .06f, .86f, .06f, .08f,
                .06f, .06f, .86f, .08f,
                0f, 0f, 0f, 1f
            )
        }
    }
}
