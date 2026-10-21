package com.example.cutline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cutline.export.ExportOptions
import com.example.cutline.export.ExportSnapshot
import com.example.cutline.export.ExportStatus
import com.example.cutline.export.ExportPreset
import com.example.cutline.export.ExportWarning
import com.example.cutline.export.ExportColorMode
import com.example.cutline.model.AudioMix
import com.example.cutline.model.TextAnimation
import com.example.cutline.model.TextStyle
import com.example.cutline.model.TransitionKind
import com.example.cutline.model.ColorAdjustments
import com.example.cutline.model.FilterPreset
import com.example.cutline.model.LayerTransform
import com.example.cutline.model.TransformKeyframe
import com.example.cutline.model.KeyframeEasing
import com.example.cutline.model.ChromaKeySettings
import com.example.cutline.model.SpeedPoint
import com.example.cutline.model.StabilizationSettings
import com.example.cutline.model.FreezeFrameSettings
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun TextEditorPanel(
    initial: TextStyle,
    onChange: (TextStyle) -> Unit
) {
    var style by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = style.text,
            onValueChange = { style = style.copy(text = it); onChange(style) },
            label = { Text("Title or caption") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Size ${style.fontSizeSp.toInt()}sp")
        Slider(value = style.fontSizeSp, onValueChange = {
            style = style.copy(fontSizeSp = it); onChange(style)
        }, valueRange = 12f..120f)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextAnimation.entries.forEach { animation ->
                FilterChip(
                    selected = style.animation == animation,
                    onClick = { style = style.copy(animation = animation); onChange(style) },
                    label = { Text(animation.name.replace('_', ' ')) }
                )
            }
        }
        Text("Karaoke captions")
        Button(onClick = { style = style.copy(karaoke = !style.karaoke); onChange(style) }) {
            Text(if (style.karaoke) "Enabled" else "Disabled")
        }
    }
}

@Composable
fun TransitionPanel(
    selected: TransitionKind,
    durationMs: Long,
    onChange: (TransitionKind, Long) -> Unit
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Transition into selected clip")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransitionKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == selected,
                    onClick = { onChange(kind, durationMs.coerceAtLeast(80L)) },
                    label = { Text(kind.name.replace('_', ' ')) }
                )
            }
        }
        Text("Duration ${(durationMs / 1000f).formatSeconds()}s")
        Slider(value = durationMs.toFloat(), onValueChange = {
            onChange(selected, it.toLong())
        }, valueRange = 80f..2000f)
    }
}

@Composable
fun AudioMixPanel(initial: AudioMix, onChange: (AudioMix) -> Unit) {
    var mix by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Volume ${(mix.volume * 100).toInt()}%")
        Slider(value = mix.volume, onValueChange = { mix = mix.copy(volume = it); onChange(mix) })
        Text("Pan ${mix.pan.formatSigned()}")
        Slider(value = mix.pan, onValueChange = { mix = mix.copy(pan = it); onChange(mix) }, valueRange = -1f..1f)
        Text("Fade in ${(mix.fadeInMs / 1000f).formatSeconds()}s")
        Slider(value = mix.fadeInMs.toFloat(), onValueChange = { mix = mix.copy(fadeInMs = it.toLong()); onChange(mix) }, valueRange = 0f..5000f)
        Text("Fade out ${(mix.fadeOutMs / 1000f).formatSeconds()}s")
        Slider(value = mix.fadeOutMs.toFloat(), onValueChange = { mix = mix.copy(fadeOutMs = it.toLong()); onChange(mix) }, valueRange = 0f..5000f)
    }
}

@Composable
fun FilterPanel(selected: FilterPreset, onChange: (FilterPreset) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Professional filters")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selected == preset,
                    onClick = { onChange(preset) },
                    label = { Text(preset.name.replace('_', ' ')) }
                )
            }
        }
    }
}

@Composable
fun ColorGradePanel(initial: ColorAdjustments, onChange: (ColorAdjustments) -> Unit, onImportLut: () -> Unit = {}) {
    var color by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Color grading")
        Text("Brightness ${(color.brightness * 100).toInt()}%")
        Slider(value = color.brightness, onValueChange = { color = color.copy(brightness = it); onChange(color) }, valueRange = -1f..1f)
        Text("Contrast ${(color.contrast * 100).toInt()}%")
        Slider(value = color.contrast, onValueChange = { color = color.copy(contrast = it); onChange(color) }, valueRange = 0f..2f)
        Text("Saturation ${(color.saturation * 100).toInt()}%")
        Slider(value = color.saturation, onValueChange = { color = color.copy(saturation = it); onChange(color) }, valueRange = 0f..2f)
        Text("Temperature ${(color.temperature * 100).toInt()}%")
        Slider(value = color.temperature, onValueChange = { color = color.copy(temperature = it); onChange(color) }, valueRange = -1f..1f)
        Button(onClick = onImportLut) { Text(if (color.lutUri == null) "Import .cube LUT" else "Replace LUT") }
        color.lutUri?.let { Text("LUT active", color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
fun SpeedPanel(initial: Float, onChange: (Float) -> Unit) {
    var speed by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Speed ${"%.2f".format(speed)}x")
        Slider(value = speed, onValueChange = { speed = it; onChange(it) }, valueRange = .25f..4f)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(.25f, .5f, 1f, 2f, 4f).forEach { value ->
                FilterChip(selected = speed == value, onClick = { speed = value; onChange(value) }, label = { Text("${value}x") })
            }
        }
    }
}

@Composable
fun SpeedCurvePanel(points: List<SpeedPoint>, durationMs: Long, onChange: (List<SpeedPoint>) -> Unit) {
    val presets = listOf(
        listOf(SpeedPoint(0, 1f), SpeedPoint(durationMs / 2, .35f), SpeedPoint(durationMs, 1f)),
        listOf(SpeedPoint(0, .5f), SpeedPoint(durationMs / 2, 2f), SpeedPoint(durationMs, .5f)),
        listOf(SpeedPoint(0, 1f), SpeedPoint(durationMs, 1f))
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Speed curve", style = MaterialTheme.typography.titleMedium)
        Text("Optical-flow processing can be connected to this curve for smoother slow motion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEachIndexed { index, preset ->
                FilterChip(selected = points == preset, onClick = { onChange(preset) }, label = { Text(listOf("Ease", "Pulse", "Normal")[index]) })
            }
        }
        points.forEach { point -> Text("${(point.timeMs / 1000f).formatSeconds()}s  ·  ${"%.2f".format(point.speed)}x") }
    }
}

@Composable
fun StabilizationPanel(
    initial: StabilizationSettings,
    analyzing: Boolean,
    errorMessage: String?,
    onAnalyze: () -> Unit,
    onChange: (StabilizationSettings) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Stabilization", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onAnalyze, enabled = !analyzing) {
            if (analyzing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Analyzing camera motion…")
                }
            } else {
                Text(if (value.analysisReady) "Re-analyze footage" else "Analyze footage")
            }
        }
        if (value.analysisReady) Text("Motion analysis ready · applied in preview and export")
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Strength ${(value.strength * 100).toInt()}%")
        Slider(value = value.strength, onValueChange = { value = value.copy(strength = it); onChange(value) }, valueRange = 0f..1f)
        Text("Crop ${value.cropPercent.toInt()}%")
        Slider(value = value.cropPercent, onValueChange = { value = value.copy(cropPercent = it); onChange(value) }, valueRange = 0f..25f)
    }
}

@Composable
fun FreezeFramePanel(
    initial: FreezeFrameSettings,
    creating: Boolean,
    canInsert: Boolean,
    errorMessage: String?,
    onCreate: () -> Unit,
    onDurationChange: (Long) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Freeze frame", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onCreate, enabled = canInsert && !creating) {
            Text(if (creating) "Capturing frame…" else "Insert freeze at playhead")
        }
        Text("The still is inserted on the selected video track; later clips on that track move by the hold duration.")
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Duration ${(value.durationMs / 1000f).formatSeconds()}s")
        Slider(value = value.durationMs.toFloat(), onValueChange = {
            value = value.copy(durationMs = it.toLong())
            onDurationChange(value.durationMs)
        }, valueRange = 100f..5000f)
    }
}

@Composable
fun KeyframeCurvePanel(
    keyframes: List<TransformKeyframe>,
    playheadMs: Long,
    initialTransform: LayerTransform,
    onAdd: (LayerTransform, KeyframeEasing) -> Unit,
    onRemove: (Long) -> Unit
) {
    var transform by remember(playheadMs, initialTransform) { mutableStateOf(initialTransform) }
    var easing by remember { mutableStateOf(KeyframeEasing.EASE_IN_OUT) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Keyframe curve editor (${keyframes.size} points)")
        Text("At ${(playheadMs / 1000f).formatSeconds()}s · Scale ${"%.2f".format(transform.scaleX)}")
        Slider(value = transform.scaleX, onValueChange = { transform = transform.copy(scaleX = it, scaleY = it) }, valueRange = .2f..3f)
        Text("Rotation ${transform.rotationDegrees.toInt()}°")
        Slider(value = transform.rotationDegrees, onValueChange = { transform = transform.copy(rotationDegrees = it) }, valueRange = -180f..180f)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyframeEasing.entries.forEach { value ->
                FilterChip(selected = easing == value, onClick = { easing = value }, label = { Text(value.name.replace('_', ' ')) })
            }
        }
        Button(onClick = { onAdd(transform, easing) }, modifier = Modifier.fillMaxWidth()) { Text("Add / update keyframe") }
        keyframes.forEach { frame ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(frame.timeMs / 1000f).formatSeconds()}s · ${frame.easing.name}")
                TextButton(onClick = { onRemove(frame.timeMs) }) { Text("Remove") }
            }
        }
    }
}

@Composable
fun ChromaKeyPanel(initial: ChromaKeySettings, onChange: (ChromaKeySettings) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("GPU chroma key")
        Button(onClick = { value = value.copy(enabled = !value.enabled); onChange(value) }) {
            Text(if (value.enabled) "Enabled · tap to disable" else "Enable green screen")
        }
        Text("Threshold ${(value.threshold * 100).toInt()}%")
        Slider(value = value.threshold, onValueChange = { value = value.copy(threshold = it); onChange(value) }, valueRange = 0.01f..1f)
        Text("Softness ${(value.softness * 100).toInt()}%")
        Slider(value = value.softness, onValueChange = { value = value.copy(softness = it); onChange(value) }, valueRange = 0.01f..1f)
        Text("Key color: green (default)")
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ExportSheet(
    snapshot: ExportSnapshot,
    options: ExportOptions,
    onOptionsChange: (ExportOptions) -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    warnings: List<ExportWarning> = emptyList(),
    canFastExport: Boolean = false,
    onFastExport: () -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Export video", style = MaterialTheme.typography.headlineSmall)
            Text("Choose a delivery preset or use smart fast export when no effects need rendering.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Platform preset")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExportPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = options.expectedWidth == preset.options.expectedWidth && options.expectedHeight == preset.options.expectedHeight,
                        onClick = { onOptionsChange(preset.options) },
                        label = { Text(preset.label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(480 to 854, 720 to 1280, 1080 to 1920, 2160 to 3840).forEach { (width, height) ->
                    FilterChip(
                        selected = options.expectedWidth == width,
                        onClick = { onOptionsChange(options.copy(expectedWidth = width, expectedHeight = height, videoBitrate = if (width == 720) 7_000_000 else 12_000_000)) },
                        label = { Text("${width}p") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60).forEach { fps ->
                    FilterChip(selected = options.expectedFrameRate == fps, onClick = { onOptionsChange(options.copy(expectedFrameRate = fps)) }, label = { Text("${fps} fps") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportColorMode.entries.forEach { mode ->
                    FilterChip(selected = options.colorMode == mode, onClick = { onOptionsChange(options.copy(colorMode = mode)) }, label = { Text(mode.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("H.264" to "video/avc", "HEVC" to "video/hevc", "AV1" to "video/av01").forEach { (label, mime) ->
                    FilterChip(
                        selected = options.videoMimeType == mime,
                        onClick = { onOptionsChange(options.copy(videoMimeType = mime)) },
                        label = { Text(label) }
                    )
                }
            }
            Text("Estimated size: ${estimateSize(snapshot, options)} MB")
            warnings.forEach { warning ->
                Text(
                    text = (if (warning.blocking) "⚠ " else "ⓘ ") + warning.message,
                    color = if (warning.blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            when {
                snapshot.status == ExportStatus.EXPORTING -> {
                    Text("Exporting ${snapshot.progress}%")
                    Slider(value = snapshot.progress.toFloat(), onValueChange = {}, valueRange = 0f..100f)
                    Button(onClick = onCancel) { Text("Cancel") }
                }
                snapshot.status == ExportStatus.COMPLETE -> {
                    Text("Export complete")
                    Button(onClick = onShare) { Text("Share video") }
                }
                else -> {
                    Button(enabled = warnings.none { it.blocking }, onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Render full video") }
                    if (canFastExport) {
                        TextButton(onClick = onFastExport, modifier = Modifier.fillMaxWidth()) {
                            Text("⚡ Fast export selected clip (stream copy)")
                        }
                    }
                }
            }
        }
    }
}

private fun estimateSize(snapshot: ExportSnapshot, options: ExportOptions): Int =
    ((options.videoBitrate + options.audioBitrate) / 8f * 30f / 1_000_000f).toInt().coerceAtLeast(1)

private fun Float.formatSeconds() = "%.1f".format(this)
private fun Float.formatSigned() = if (this >= 0) "+%.1f".format(this) else "%.1f".format(this)
