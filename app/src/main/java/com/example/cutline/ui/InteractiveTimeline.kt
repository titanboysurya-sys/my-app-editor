package com.example.cutline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TimelineTrack
import com.example.cutline.model.TrackKind
import kotlin.math.roundToInt

@Composable
fun InteractiveTimeline(
    tracks: List<TimelineTrack>,
    playheadMs: Long,
    onMoveClip: (String, Long) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit,
    onSplitClip: (String) -> Unit,
    onSelectClip: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var selectedClip by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val pxPerSecond = 72f * zoom
    val durationMs = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndMs } ?: 30_000L
    val width = ((durationMs / 1_000f) * pxPerSecond + 160f).dp

    Column(modifier = modifier) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Timeline", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = zoom,
                onValueChange = { zoom = it },
                valueRange = .5f..3f,
                modifier = Modifier.width(160.dp)
            )
            Text("${zoom.roundToInt()}x")
            Spacer(Modifier.weight(1f))
            selectedClip?.let { id ->
                IconButton(onClick = { onSplitClip(id) }) { Text("✂") }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1020))
        ) {
            Row(modifier = Modifier.horizontalScroll(scroll)) {
                Column(modifier = Modifier.width(width).padding(8.dp)) {
                    TimeRuler(durationMs, pxPerSecond)
                    tracks.sortedBy { it.index }.forEach { track ->
                        TrackRow(
                            track = track,
                            pxPerSecond = pxPerSecond,
                            selectedClip = selectedClip,
                            onSelect = { selectedClip = it; onSelectClip(it) },
                            onMoveClip = onMoveClip,
                            onTrimClip = onTrimClip
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset((playheadMs / 1_000f * pxPerSecond).roundToInt(), 0) }
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color(0xFFFF4D67))
            )
        }
    }
}

@Composable
private fun TimeRuler(durationMs: Long, pxPerSecond: Float) {
    Row(modifier = Modifier.width(((durationMs / 1_000f) * pxPerSecond + 120f).dp)) {
        var second = 0
        while (second <= durationMs / 1_000L) {
            Text(
                text = "${second}s",
                color = Color.LightGray,
                modifier = Modifier.width((pxPerSecond).dp)
            )
            second++
        }
    }
}

@Composable
private fun TrackRow(
    track: TimelineTrack,
    pxPerSecond: Float,
    selectedClip: String?,
    onSelect: (String) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit
) {
    val color = when (track.kind) {
        TrackKind.VIDEO -> Color(0xFF2563EB)
        TrackKind.OVERLAY -> Color(0xFFB026A6)
        TrackKind.AUDIO -> Color(0xFF059669)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF151D30), RoundedCornerShape(8.dp))
    ) {
        track.clips.forEach { clip ->
            ClipBlock(
                clip = clip,
                color = color,
                pxPerSecond = pxPerSecond,
                selected = selectedClip == clip.id,
                onSelect = { onSelect(clip.id) },
                onMove = { deltaPx ->
                    onMoveClip(clip.id, (deltaPx / pxPerSecond * 1_000f).roundToInt().toLong())
                },
                onTrim = { left, right ->
                    onTrimClip(
                        clip.id,
                        (left / pxPerSecond * 1_000f).roundToInt().toLong(),
                        (right / pxPerSecond * 1_000f).roundToInt().toLong()
                    )
                }
            )
        }
    }
}

@Composable
private fun ClipBlock(
    clip: TimelineClip,
    color: Color,
    pxPerSecond: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (Float) -> Unit,
    onTrim: (Float, Float) -> Unit
) {
    val width = (clip.durationMs / 1_000f * pxPerSecond).coerceAtLeast(48f)
    var dragTotal by remember(clip.id) { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .offset { IntOffset((clip.timelineStartMs / 1_000f * pxPerSecond).roundToInt(), 0) }
            .width(width.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = .25f) else color)
            .pointerInput(clip.id) {
                detectDragGestures(
                    onDragStart = { dragTotal = 0f; onSelect() },
                    onDragCancel = { dragTotal = 0f },
                    onDragEnd = { onMove(dragTotal); dragTotal = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragTotal += amount.x
                    }
                )
            }
            .padding(8.dp)
    ) {
        MediaThumbnail(
            clip = clip,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = "${clip.id.take(5)}  ${(clip.durationMs / 1_000f).roundToInt()}s",
            color = Color.White
        )
        if (selected) {
            TrimHandle(Modifier.align(androidx.compose.ui.Alignment.CenterStart)) { delta -> onTrim(delta, 0f) }
            TrimHandle(Modifier.align(androidx.compose.ui.Alignment.CenterEnd)) { delta -> onTrim(0f, -delta) }
        }
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, onTrim: (Float) -> Unit) {
    Box(
        modifier = modifier
            .size(width = 8.dp, height = 32.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onTrim(amount.x)
                }
            }
    )
}

@Composable
fun WaveformStrip(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF58D68D)
) {
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val barWidth = size.width / samples.size
        samples.forEachIndexed { index, value ->
            val amplitude = (value.coerceIn(0f, 1f) * size.height / 2f).coerceAtLeast(2f)
            val x = index * barWidth + barWidth / 2f
            drawLine(color, Offset(x, size.height / 2f - amplitude), Offset(x, size.height / 2f + amplitude), barWidth * .6f)
        }
    }
}
