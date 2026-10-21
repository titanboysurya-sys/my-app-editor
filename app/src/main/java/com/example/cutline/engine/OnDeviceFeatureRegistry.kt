package com.example.cutline.engine

import android.content.Context
import java.io.File

enum class OnDeviceFeature { WHISPER_CAPTIONS, OPTICAL_FLOW, STABILIZATION, BACKGROUND_REMOVAL, MOTION_TRACKING, HDR_RENDER }

enum class FeatureAvailability { READY, MODEL_REQUIRED, DEVICE_UNSUPPORTED, NOT_IMPLEMENTED }

data class FeatureCapability(
    val feature: OnDeviceFeature,
    val availability: FeatureAvailability,
    val detail: String
)

/** Central capability contract so the UI never presents an engine as available when it is not. */
class OnDeviceFeatureRegistry(private val context: Context) {
    fun inspect(): List<FeatureCapability> = listOf(
        capability(OnDeviceFeature.WHISPER_CAPTIONS, "models/whisper-tiny.en.onnx", "Bundled Whisper model required"),
        capability(OnDeviceFeature.OPTICAL_FLOW, "models/optical-flow.bin", "Optical-flow model required"),
        capability(OnDeviceFeature.STABILIZATION, "models/stabilization.bin", "Motion-analysis model required"),
        capability(OnDeviceFeature.BACKGROUND_REMOVAL, "selfie_segmenter.tflite", "MediaPipe segmentation model required"),
        capability(OnDeviceFeature.MOTION_TRACKING, "models/motion-tracking.bin", "Tracking model required"),
        hdrCapability()
    )

    private fun capability(feature: OnDeviceFeature, assetPath: String, missing: String): FeatureCapability {
        val installed = File(context.filesDir, assetPath).isFile || runCatching { context.assets.open(assetPath).close(); true }.getOrDefault(false)
        return FeatureCapability(feature, if (installed) FeatureAvailability.READY else FeatureAvailability.MODEL_REQUIRED, if (installed) "On-device model ready" else missing)
    }

    private fun hdrCapability(): FeatureCapability = FeatureCapability(
        OnDeviceFeature.HDR_RENDER,
        if (android.os.Build.VERSION.SDK_INT >= 26) FeatureAvailability.READY else FeatureAvailability.DEVICE_UNSUPPORTED,
        if (android.os.Build.VERSION.SDK_INT >= 26) "Device HDR pipeline can be probed before export" else "HDR requires Android 8 or newer"
    )
}
