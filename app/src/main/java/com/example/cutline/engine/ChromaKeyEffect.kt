package com.example.cutline.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import com.example.cutline.model.ChromaKeySettings

/** GPU chroma-key effect. Pixels near the configured key colour become transparent. */
@UnstableApi
class ChromaKeyEffect(private val settings: ChromaKeySettings) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        Program(context, settings, useHdr)

    private class Program(
        context: Context,
        private val settings: ChromaKeySettings,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, 2) {
        private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        private var width = 0
        private var height = 0

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            width = inputWidth
            height = inputHeight
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                val color = settings.keyColorArgb
                program.setFloatsUniform("uKeyColor", floatArrayOf(
                    ((color shr 16) and 0xff) / 255f,
                    ((color shr 8) and 0xff) / 255f,
                    (color and 0xff) / 255f
                ))
                program.setFloatUniform("uThreshold", settings.threshold.coerceIn(0f, 1f))
                program.setFloatUniform("uSoftness", settings.softness.coerceIn(.001f, 1f))
                program.setBufferAttribute("aFramePosition", GlUtil.createVertexBuffer(NDC), 4)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
                GlUtil.checkGlError()
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            runCatching { program.delete() }
        }
    }

    private companion object {
        val NDC = listOf(
            floatArrayOf(-1f, -1f, 0f, 1f), floatArrayOf(-1f, 1f, 0f, 1f),
            floatArrayOf(1f, 1f, 0f, 1f), floatArrayOf(1f, -1f, 0f, 1f)
        )
        const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexCoords;
            void main() {
                gl_Position = aFramePosition;
                vTexCoords = aFramePosition.xy * 0.5 + 0.5;
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec3 uKeyColor;
            uniform float uThreshold;
            uniform float uSoftness;
            varying vec2 vTexCoords;
            void main() {
                vec4 source = texture2D(uTexSampler, vTexCoords);
                float distanceFromKey = distance(source.rgb, uKeyColor);
                float alpha = smoothstep(uThreshold, uThreshold + uSoftness, distanceFromKey);
                gl_FragColor = vec4(source.rgb, source.a * alpha);
            }
        """
    }
}
