package com.example.cutline.engine

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.effect.StaticOverlaySettings
import com.example.cutline.model.BlendMode
import com.example.cutline.model.LayerTransform

data class CompositorLayer(
    val inputId: Int,
    val transform: LayerTransform,
    val blendMode: BlendMode
)

class CutlineCompositorSettings(
    width: Int,
    height: Int,
    layers: List<CompositorLayer>
) : VideoCompositorSettings {
    private val outputSize = Size(width.coerceAtLeast(1), height.coerceAtLeast(1))
    private val settings = layers.associate { layer ->
        layer.inputId to StaticOverlaySettings.Builder()
            .setAlphaScale(layer.transform.opacity.coerceIn(0f, 1f))
            .setScale(layer.transform.scaleX, layer.transform.scaleY)
            .setRotationDegrees(layer.transform.rotationDegrees)
            .setBackgroundFrameAnchor(
                layer.transform.positionX.coerceIn(-1f, 1f),
                layer.transform.positionY.coerceIn(-1f, 1f)
            )
            .build()
    }

    override fun getOutputSize(inputSizes: MutableList<Size>): Size = outputSize

    override fun getOverlaySettings(
        inputId: Int,
        presentationTimeUs: Long
    ): OverlaySettings = settings[inputId] ?: StaticOverlaySettings.Builder().build()

    /** Media3 exposes alpha and transforms here; blendMode needs a custom GL compositor. */
    fun blendModeFor(inputId: Int): BlendMode = layers.firstOrNull { it.inputId == inputId }?.blendMode
        ?: BlendMode.NORMAL

    private val layers = layers.associateBy { it.inputId }
}
