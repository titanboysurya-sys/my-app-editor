package com.example.cutline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import com.example.cutline.model.AudioMix
import com.example.cutline.model.TextAnimation
import com.example.cutline.model.TextStyle
import com.example.cutline.model.TransitionKind

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
fun ExportSheet(
    snapshot: ExportSnapshot,
    options: ExportOptions,
    onOptionsChange: (ExportOptions) -> Unit,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Export video")
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
                listOf("H.264" to "video/avc", "HEVC" to "video/hevc", "AV1" to "video/av01").forEach { (label, mime) ->
                    FilterChip(
                        selected = options.videoMimeType == mime,
                        onClick = { onOptionsChange(options.copy(videoMimeType = mime)) },
                        label = { Text(label) }
                    )
                }
            }
            Text("Estimated size: ${estimateSize(snapshot, options)} MB")
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
                else -> Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export") }
            }
        }
    }
}

private fun estimateSize(snapshot: ExportSnapshot, options: ExportOptions): Int =
    ((options.videoBitrate + options.audioBitrate) / 8f * 30f / 1_000_000f).toInt().coerceAtLeast(1)

private fun Float.formatSeconds() = "%.1f".format(this)
private fun Float.formatSigned() = if (this >= 0) "+%.1f".format(this) else "%.1f".format(this)
