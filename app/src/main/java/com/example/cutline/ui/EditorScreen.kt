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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import com.example.cutline.export.ExportPreflight
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
    val stabilizationRunning by editorViewModel.stabilizationRunning.collectAsState()
    val analysisError by editorViewModel.analysisError.collectAsState()
    val backgroundRemovalRunning by editorViewModel.backgroundRemovalRunning.collectAsState()
    val backgroundRemovalError by editorViewModel.backgroundRemovalError.collectAsState()
    val freezeFrameRunning by editorViewModel.freezeFrameRunning.collectAsState()
    val freezeFrameError by editorViewModel.freezeFrameError.collectAsState()
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
                previewController.setComposition(
                    TimelineCompositionConverter.build(tracks, CompositionOptions(effectContext = context))
                )
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
    val lutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectedClip?.let { clip ->
            editorViewModel.updateColor(clip.id, clip.colorAdjustments.copy(lutUri = uri))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("CUTLINE", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Edit locally · export confidently", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }) {
                Text("＋ Import")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(enabled = canUndo, onClick = editorViewModel::undo) { Text("Undo") }
            TextButton(enabled = canRedo, onClick = editorViewModel::redo) { Text("Redo") }
            selectedClipId?.let { id ->
                Button(onClick = { editorViewModel.duplicateClip(id) }) { Text("Duplicate") }
                Button(onClick = { editorViewModel.deleteClip(id); selectedClipId = null }) { Text("Delete") }
                TextButton(onClick = { editorViewModel.copyClipStyle(id) }) { Text("Copy style") }
                TextButton(onClick = { editorViewModel.pasteClipStyle(id) }) { Text("Paste style") }
                selectedClip?.takeIf { !it.isStillImage }?.let { clip ->
                    TextButton(onClick = {
                        exportController.fastTrimSingleClip(
                            clip.source,
                            clip.sourceStartMs,
                            clip.sourceEndMs,
                            File(context.cacheDir, "cutline-fast-trim-${System.currentTimeMillis()}.mp4")
                        )
                    }) { Text("Fast trim") }
                }
            }
            FilterChip(selected = panel == "text", onClick = { panel = "text" }, label = { Text("Text") })
            FilterChip(selected = panel == "transition", onClick = { panel = "transition" }, label = { Text("Transition") })
            FilterChip(selected = panel == "audio", onClick = { panel = "audio" }, label = { Text("Audio mix") })
            FilterChip(selected = panel == "filters", onClick = { panel = "filters" }, label = { Text("Filters") })
            FilterChip(selected = panel == "color", onClick = { panel = "color" }, label = { Text("Color") })
            FilterChip(selected = panel == "speed", onClick = { panel = "speed" }, label = { Text("Speed") })
            FilterChip(selected = panel == "keyframes", onClick = { panel = "keyframes" }, label = { Text("Keyframes") })
            FilterChip(selected = panel == "chroma", onClick = { panel = "chroma" }, label = { Text("Chroma key") })
            FilterChip(selected = panel == "speedCurve", onClick = { panel = "speedCurve" }, label = { Text("Speed curve") })
            FilterChip(selected = panel == "stabilize", onClick = { panel = "stabilize" }, label = { Text("Stabilize") })
            FilterChip(selected = panel == "freeze", onClick = { panel = "freeze" }, label = { Text("Freeze") })
            selectedClip?.takeIf { it.isStillImage }?.let { clip ->
                FilterChip(
                    selected = false,
                    enabled = !backgroundRemovalRunning,
                    onClick = { editorViewModel.removeImageBackground(clip.id) },
                    label = { Text(if (backgroundRemovalRunning) "Cutting out..." else "Remove background") }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 220.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1320))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
        }

        Surface(tonalElevation = 3.dp, shape = MaterialTheme.shapes.large) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (isPlaying) previewController.pause() else previewController.play()
                isPlaying = !isPlaying
            }) { Text(if (isPlaying) "Pause" else "Play") }
            Button(onClick = { playheadMs = (playheadMs - 33L).coerceAtLeast(0L); previewController.seekTo(playheadMs) }) { Text("Frame -") }
            Button(onClick = { playheadMs += 33L; previewController.seekTo(playheadMs) }) { Text("Frame +") }
            Text("${playheadMs / 1000f}s", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            InteractiveTimeline(
                tracks = tracks,
                playheadMs = playheadMs,
                onMoveClip = editorViewModel::moveClip,
                onTrimClip = editorViewModel::trimClip,
                onSplitClip = { id ->
                    val clip = tracks.flatMap { it.clips }.firstOrNull { it.id == id }
                    if (clip != null) editorViewModel.splitClip(id, clip.timelineStartMs + clip.durationMs / 2)
                },
                onSelectClip = { selectedClipId = it },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }

        selectedClip?.let { clip ->
            backgroundRemovalError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp))
            }
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
                "filters" -> FilterPanel(
                    selected = clip.filter,
                    onChange = { editorViewModel.updateFilter(clip.id, it) }
                )
                "color" -> ColorGradePanel(
                    initial = clip.colorAdjustments,
                    onChange = { editorViewModel.updateColor(clip.id, it) },
                    onImportLut = { lutPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
                )
                "speed" -> SpeedPanel(
                    initial = clip.speed,
                    onChange = { editorViewModel.updateSpeed(clip.id, it) }
                )
                "keyframes" -> KeyframeCurvePanel(
                    keyframes = clip.transformKeyframes,
                    playheadMs = (playheadMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs),
                    initialTransform = clip.transform,
                    onAdd = { transform, easing ->
                        editorViewModel.addTransformKeyframe(
                            clip.id,
                            (playheadMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs),
                            transform,
                            easing
                        )
                    },
                    onRemove = { editorViewModel.removeTransformKeyframe(clip.id, it) }
                )
                "chroma" -> ChromaKeyPanel(
                    initial = clip.chromaKey,
                    onChange = { editorViewModel.updateChromaKey(clip.id, it) }
                )
                "speedCurve" -> SpeedCurvePanel(
                    points = clip.speedCurve,
                    durationMs = clip.sourceTrimDurationMs,
                    onChange = { editorViewModel.setSpeedCurve(clip.id, it) }
                )
                "stabilize" -> StabilizationPanel(
                    initial = clip.stabilization,
                    analyzing = stabilizationRunning,
                    errorMessage = analysisError,
                    onAnalyze = { editorViewModel.analyzeStabilization(clip.id) },
                    onChange = { editorViewModel.updateStabilization(clip.id, it) }
                )
                "freeze" -> FreezeFramePanel(
                    initial = clip.freezeFrame,
                    creating = freezeFrameRunning,
                    canInsert = !clip.isStillImage && playheadMs > clip.timelineStartMs && playheadMs < clip.timelineEndMs,
                    errorMessage = freezeFrameError,
                    onCreate = { editorViewModel.insertFreezeFrame(clip.id, playheadMs, clip.freezeFrame.durationMs) },
                    onDurationChange = { editorViewModel.updateFreezeFrame(clip.id, clip.freezeFrame.copy(durationMs = it)) }
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
                        outputHeight = exportOptions.expectedHeight,
                        effectContext = context
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
            },
            warnings = ExportPreflight.check(tracks, exportOptions)
            , canFastExport = selectedClip?.let { !it.isStillImage && tracks.size == 1 && tracks.all { track -> track.clips.size == 1 } } == true
            , onFastExport = {
                selectedClip?.let { clip ->
                    exportController.fastTrimSingleClip(
                        clip.source, clip.sourceStartMs, clip.sourceEndMs,
                        File(context.cacheDir, "cutline-fast-${System.currentTimeMillis()}.mp4")
                    )
                }
            }
        )
    }
}
