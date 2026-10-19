package com.example.cutline.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cutline.engine.TimelineCompositionConverter
import com.example.cutline.export.ExportController
import com.example.cutline.export.ExportOptions
import com.example.cutline.engine.CompositionOptions
import com.example.cutline.engine.PreviewPlayerController
import com.example.cutline.model.TextStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel = viewModel(),
    exportController: ExportController
) {
    val tracks by editorViewModel.tracks.collectAsState()
    val canUndo by editorViewModel.canUndo.collectAsState()
    val canRedo by editorViewModel.canRedo.collectAsState()
    val exportSnapshot by exportController.snapshot.collectAsState()
    val context = LocalContext.current
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf<String?>(null) }
    var exportOptions by remember { mutableStateOf(ExportOptions()) }
    var isPlaying by remember { mutableStateOf(false) }
    var playheadMs by remember { mutableStateOf(0L) }
    val previewController = remember(context) { PreviewPlayerController(context) }
    val selectedClip = tracks.flatMap { it.clips }.firstOrNull { it.id == selectedClipId }

    DisposableEffect(previewController) {
        onDispose { previewController.release() }
    }
    LaunchedEffect(tracks) {
        if (tracks.any { it.clips.isNotEmpty() }) {
            runCatching {
                previewController.setComposition(TimelineCompositionConverter.build(tracks))
            }
        }
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        editorViewModel.importUris(uris)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Cutline", style = MaterialTheme.typography.headlineMedium)
                Text("Offline multi-track editor")
            }
            Button(onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }) {
                Text("Import")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(enabled = canUndo, onClick = editorViewModel::undo) { Text("Undo") }
            Button(enabled = canRedo, onClick = editorViewModel::redo) { Text("Redo") }
            selectedClipId?.let { id ->
                Button(onClick = { editorViewModel.duplicateClip(id) }) { Text("Duplicate") }
                Button(onClick = { editorViewModel.deleteClip(id); selectedClipId = null }) { Text("Delete") }
            }
            FilterChip(selected = panel == "text", onClick = { panel = "text" }, label = { Text("Text") })
            FilterChip(selected = panel == "transition", onClick = { panel = "transition" }, label = { Text("Transition") })
            FilterChip(selected = panel == "audio", onClick = { panel = "audio" }, label = { Text("Audio mix") })
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF111827)),
        ) {
            AndroidView(
                factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply {
                        useController = false
                        player = previewController.player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            selectedClip?.textStyle?.let { style -> TextOverlayPreview(style) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (isPlaying) previewController.pause() else previewController.play()
                isPlaying = !isPlaying
            }) { Text(if (isPlaying) "Pause" else "Play") }
            Button(onClick = { playheadMs = (playheadMs - 33L).coerceAtLeast(0L); previewController.seekTo(playheadMs) }) { Text("Frame -") }
            Button(onClick = { playheadMs += 33L; previewController.seekTo(playheadMs) }) { Text("Frame +") }
            Text("${playheadMs / 1000f}s", color = Color.Gray)
        }

        InteractiveTimeline(
            tracks = tracks,
            playheadMs = 0L,
            onMoveClip = editorViewModel::moveClip,
            onTrimClip = editorViewModel::trimClip,
            onSplitClip = { id ->
                val clip = tracks.flatMap { it.clips }.firstOrNull { it.id == id }
                if (clip != null) {
                    editorViewModel.splitClip(id, clip.timelineStartMs + clip.durationMs / 2)
                }
            },
            onSelectClip = { selectedClipId = it },
            modifier = Modifier.fillMaxWidth()
        )

        selectedClip?.let { clip ->
            when (panel) {
                "text" -> TextEditorPanel(
                    initial = clip.textStyle ?: TextStyle("Your title"),
                    onChange = { editorViewModel.updateTextStyle(clip.id, it) }
                )
                "transition" -> TransitionPanel(
                    selected = clip.transitionIn,
                    durationMs = clip.transitionDurationMs.coerceAtLeast(250L),
                    onChange = { kind, duration -> editorViewModel.setTransition(clip.id, kind, duration) }
                )
                "audio" -> AudioMixPanel(
                    initial = clip.audioMix,
                    onChange = { editorViewModel.updateAudioMix(clip.id, it) }
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }) {
                Text("Add media")
            }
            Button(onClick = { panel = "export" }) { Text("Export") }
        }
    }

    if (panel == "export") {
        ExportSheet(
            snapshot = exportSnapshot,
            options = exportOptions,
            onOptionsChange = { exportOptions = it },
            onExport = {
                val output = File(context.cacheDir, "cutline-export-${System.currentTimeMillis()}.mp4")
                val built = TimelineCompositionConverter.build(
                    tracks,
                    CompositionOptions(
                        outputWidth = exportOptions.expectedWidth,
                        outputHeight = exportOptions.expectedHeight
                    )
                )
                exportController.start(built, output, exportOptions)
            },
            onCancel = exportController::cancel,
            onDismiss = { panel = null },
            onShare = {
                exportSnapshot.outputPath?.let { path ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share video"))
                }
            }
        )
    }
}
