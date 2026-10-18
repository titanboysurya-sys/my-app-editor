package com.example.cutline.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cutline.media.MediaIngestor
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TimelineTrack
import com.example.cutline.model.TrackKind
import com.example.cutline.model.AudioMix
import com.example.cutline.model.TextStyle
import com.example.cutline.model.TransitionKind
import com.example.cutline.storage.ProjectStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val ingestor = MediaIngestor(application.contentResolver)
    private val projectStore = ProjectStore(application)
    private val _tracks = MutableStateFlow<List<TimelineTrack>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack>> = _tracks.asStateFlow()
    private val undoStack = ArrayDeque<List<TimelineTrack>>()
    private val redoStack = ArrayDeque<List<TimelineTrack>>()

    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        _tracks.value = projectStore.load()
    }

    private fun edit(transform: (List<TimelineTrack>) -> List<TimelineTrack>) {
        undoStack.addLast(_tracks.value)
        _tracks.value = transform(_tracks.value)
        projectStore.save(_tracks.value)
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_tracks.value)
        _tracks.value = undoStack.removeLast()
        projectStore.save(_tracks.value)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_tracks.value)
        _tracks.value = redoStack.removeLast()
        projectStore.save(_tracks.value)
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            val imported = uris.map { uri -> ingestor.inspect(uri) }
            edit { current ->
                val videoTrack = current.firstOrNull { it.kind == TrackKind.VIDEO }
                    ?: TimelineTrack("video", 0, TrackKind.VIDEO, emptyList())
                val audioTrack = current.firstOrNull { it.kind == TrackKind.AUDIO }
                    ?: TimelineTrack("audio", 2, TrackKind.AUDIO, emptyList())
                val videoClips = videoTrack.clips.toMutableList()
                val audioClips = audioTrack.clips.toMutableList()
                var videoCursor = videoClips.maxOfOrNull { it.timelineEndMs } ?: 0L
                var audioCursor = audioClips.maxOfOrNull { it.timelineEndMs } ?: 0L
                imported.forEach { asset ->
                    val duration = asset.durationMs.coerceAtLeast(1L)
                    if (asset.hasVideo) {
                        videoClips += TimelineClip(
                            id = UUID.randomUUID().toString(),
                            source = asset.uri,
                            timelineStartMs = videoCursor,
                            sourceEndMs = duration,
                            sourceDurationMs = duration,
                            isStillImage = asset.width == 0 && asset.height == 0
                        )
                        videoCursor = videoClips.last().timelineEndMs
                    }
                    if (asset.hasAudio) {
                        audioClips += TimelineClip(
                            id = UUID.randomUUID().toString(),
                            source = asset.uri,
                            timelineStartMs = audioCursor,
                            sourceEndMs = duration,
                            sourceDurationMs = duration
                        )
                        audioCursor = audioClips.last().timelineEndMs
                    }
                }
                listOf(videoTrack.copy(clips = videoClips), audioTrack.copy(clips = audioClips)) +
                    current.filterNot { it.id == videoTrack.id || it.id == audioTrack.id }
            }
        }
    }

    fun moveClip(clipId: String, deltaMs: Long) {
        edit { tracks ->
            tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(
                        timelineStartMs = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L)
                    ) else clip
                }.sortedBy { it.timelineStartMs })
            }
        }
    }

    fun trimClip(clipId: String, leftDeltaMs: Long, rightDeltaMs: Long) {
        edit { tracks -> tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id != clipId || clip.isStillImage) return@map clip
                val start = (clip.sourceStartMs + leftDeltaMs)
                    .coerceIn(0L, clip.sourceEndMs - 1L)
                val end = (clip.sourceEndMs - rightDeltaMs)
                    .coerceIn(start + 1L, clip.sourceDurationMs)
                clip.copy(
                    sourceStartMs = start,
                    sourceEndMs = end,
                    timelineStartMs = (clip.timelineStartMs + leftDeltaMs).coerceAtLeast(0L)
                )
            })
        } }
    }

    fun splitClip(clipId: String, atTimelineMs: Long) {
        edit { tracks -> tracks.map { track ->
            val index = track.clips.indexOfFirst { it.id == clipId }
            if (index < 0) return@map track
            val clip = track.clips[index]
            val local = (atTimelineMs - clip.timelineStartMs).coerceIn(1L, clip.durationMs - 1L)
            val sourceCut = clip.sourceStartMs + (local * clip.speed).toLong()
            val first = clip.copy(id = UUID.randomUUID().toString(), sourceEndMs = sourceCut)
            val second = clip.copy(
                id = UUID.randomUUID().toString(),
                timelineStartMs = atTimelineMs,
                sourceStartMs = sourceCut
            )
            track.copy(clips = track.clips.toMutableList().apply {
                removeAt(index); add(index, first); add(index + 1, second)
            })
        } }
    }

    fun deleteClip(clipId: String) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.filterNot { it.id == clipId }) }
    }

    fun duplicateClip(clipId: String) = edit { tracks ->
        tracks.map { track ->
            val clip = track.clips.firstOrNull { it.id == clipId } ?: return@map track
            val copy = clip.copy(
                id = UUID.randomUUID().toString(),
                timelineStartMs = clip.timelineEndMs
            )
            track.copy(clips = (track.clips + copy).sortedBy { it.timelineStartMs })
        }
    }

    fun reorderClip(clipId: String, targetIndex: Int) = edit { tracks ->
        tracks.map { track ->
            val current = track.clips.indexOfFirst { it.id == clipId }
            if (current < 0) return@map track
            val clips = track.clips.toMutableList()
            val item = clips.removeAt(current)
            clips.add(targetIndex.coerceIn(0, clips.size), item)
            var cursor = 0L
            track.copy(clips = clips.map { clip ->
                clip.copy(timelineStartMs = cursor).also { cursor += it.durationMs }
            })
        }
    }

    fun updateTextStyle(clipId: String, style: TextStyle) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(textStyle = style) else clip
        }) }
    }

    fun updateAudioMix(clipId: String, mix: AudioMix) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(audioMix = mix) else clip
        }) }
    }

    fun setTransition(clipId: String, kind: TransitionKind, durationMs: Long) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(transitionIn = kind, transitionDurationMs = durationMs) else clip
        }) }
    }
}
