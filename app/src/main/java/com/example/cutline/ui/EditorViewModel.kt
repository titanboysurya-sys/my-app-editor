package com.example.cutline.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.cutline.media.MediaIngestor
import com.example.cutline.model.TimelineClip
import com.example.cutline.model.TimelineTrack
import com.example.cutline.model.TrackKind
import com.example.cutline.model.AudioMix
import com.example.cutline.model.TextStyle
import com.example.cutline.model.FilterPreset
import com.example.cutline.model.ColorAdjustments
import com.example.cutline.model.TransitionKind
import com.example.cutline.model.TransformKeyframe
import com.example.cutline.model.LayerTransform
import com.example.cutline.model.KeyframeEasing
import com.example.cutline.model.ChromaKeySettings
import com.example.cutline.model.MaskSettings
import com.example.cutline.model.BlurSettings
import com.example.cutline.model.MotionTrackingSettings
import com.example.cutline.model.AudioEnhancementSettings
import com.example.cutline.model.SpeedPoint
import com.example.cutline.model.CaptionCue
import com.example.cutline.model.StabilizationSettings
import com.example.cutline.model.FreezeFrameSettings
import com.example.cutline.model.SpeedCurveMath
import com.example.cutline.engine.StabilizationAnalyzer
import com.example.cutline.engine.BackgroundRemovalEngine
import com.example.cutline.engine.FreezeFrameCreator
import com.example.cutline.engine.KeyframeInterpolator
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
    private val stabilizationAnalyzer = StabilizationAnalyzer(application)
    private val backgroundRemovalEngine = BackgroundRemovalEngine(application)
    private val freezeFrameCreator = FreezeFrameCreator(application)
    private val _tracks = MutableStateFlow<List<TimelineTrack>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack>> = _tracks.asStateFlow()
    private val undoStack = ArrayDeque<List<TimelineTrack>>()
    private val redoStack = ArrayDeque<List<TimelineTrack>>()
    private var styleClipboard: TimelineClip? = null

    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    private val _stabilizationRunning = MutableStateFlow(false)
    val stabilizationRunning: StateFlow<Boolean> = _stabilizationRunning.asStateFlow()
    private val _backgroundRemovalRunning = MutableStateFlow(false)
    val backgroundRemovalRunning: StateFlow<Boolean> = _backgroundRemovalRunning.asStateFlow()
    private val _backgroundRemovalError = MutableStateFlow<String?>(null)
    val backgroundRemovalError: StateFlow<String?> = _backgroundRemovalError.asStateFlow()
    private val _freezeFrameRunning = MutableStateFlow(false)
    val freezeFrameRunning: StateFlow<Boolean> = _freezeFrameRunning.asStateFlow()
    private val _freezeFrameError = MutableStateFlow<String?>(null)
    val freezeFrameError: StateFlow<String?> = _freezeFrameError.asStateFlow()

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
                val moving = track.clips.firstOrNull { it.id == clipId } ?: return@map track
                val proposed = (moving.timelineStartMs + deltaMs).coerceAtLeast(0L)
                val otherEdges = track.clips.filterNot { it.id == clipId }
                    .flatMap { listOf(it.timelineStartMs, it.timelineEndMs) }
                val snapped = otherEdges.minByOrNull { kotlin.math.abs(it - proposed) }
                    ?.takeIf { kotlin.math.abs(it - proposed) <= 120L }
                    ?: proposed
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == clipId) clip.copy(
                        timelineStartMs = snapped
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
            val sourceCut = clip.sourceStartMs + SpeedCurveMath.sourceOffsetAtOutput(
                local, clip.sourceTrimDurationMs, clip.speedCurve, clip.speed
            )
            val sourceOffset = sourceCut - clip.sourceStartMs
            val firstCurve = speedCurveSegment(clip, 0L, sourceOffset)
            val secondCurve = speedCurveSegment(clip, sourceOffset, clip.sourceTrimDurationMs)
            val first = clip.copy(
                id = UUID.randomUUID().toString(),
                sourceEndMs = sourceCut,
                speedCurve = firstCurve,
                transformKeyframes = clip.transformKeyframes.filter { it.timeMs <= sourceOffset },
                stabilizationKeyframes = clip.stabilizationKeyframes.filter { it.timeMs <= sourceOffset }
            )
            val second = clip.copy(
                id = UUID.randomUUID().toString(),
                timelineStartMs = atTimelineMs,
                sourceStartMs = sourceCut,
                speedCurve = secondCurve,
                transformKeyframes = clip.transformKeyframes.filter { it.timeMs >= sourceOffset }
                    .map { it.copy(timeMs = it.timeMs - sourceOffset) },
                stabilizationKeyframes = clip.stabilizationKeyframes.filter { it.timeMs >= sourceOffset }
                    .map { it.copy(timeMs = it.timeMs - sourceOffset) }
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

    fun updateFilter(clipId: String, filter: FilterPreset) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(filter = filter) else clip
        }) }
    }

    fun updateColor(clipId: String, adjustments: ColorAdjustments) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(colorAdjustments = adjustments) else clip
        }) }
    }

    fun updateChromaKey(clipId: String, settings: ChromaKeySettings) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(chromaKey = settings) else clip
        }) }
    }

    fun updateSpeed(clipId: String, speed: Float) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(speed = speed.coerceIn(.25f, 4f)) else clip
        }) }
    }

    fun updateMask(clipId: String, value: MaskSettings) = edit { tracks -> updateClip(tracks, clipId) { it.copy(mask = value) } }

    fun updateBlur(clipId: String, value: BlurSettings) = edit { tracks -> updateClip(tracks, clipId) { it.copy(blur = value) } }

    fun updateMotionTracking(clipId: String, value: MotionTrackingSettings) = edit { tracks -> updateClip(tracks, clipId) { it.copy(motionTracking = value) } }

    fun updateAudioEnhancement(clipId: String, value: AudioEnhancementSettings) = edit { tracks -> updateClip(tracks, clipId) { it.copy(audioEnhancement = value) } }

    fun setSpeedCurve(clipId: String, points: List<SpeedPoint>) = edit { tracks ->
        updateClip(tracks, clipId) { it.copy(speedCurve = points.sortedBy { point -> point.timeMs }) }
    }

    fun setCaptions(clipId: String, cues: List<CaptionCue>) = edit { tracks ->
        updateClip(tracks, clipId) { it.copy(captions = cues.sortedBy { cue -> cue.startMs }) }
    }

    fun updateStabilization(clipId: String, value: StabilizationSettings) = edit { tracks ->
        updateClip(tracks, clipId) { clip ->
            val analysisStillMatches = clip.stabilization.strength == value.strength &&
                clip.stabilization.cropPercent == value.cropPercent
            clip.copy(stabilization = if (analysisStillMatches) value else value.copy(analysisReady = false))
        }
    }

    fun analyzeStabilization(clipId: String) {
        if (_stabilizationRunning.value) return
        val clip = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        if (clip.isStillImage) return
        viewModelScope.launch {
            _stabilizationRunning.value = true
            _analysisError.value = null
            try {
                val frames = withContext(Dispatchers.IO) { stabilizationAnalyzer.analyze(clip, clip.stabilization) }
                edit { tracks -> updateClip(tracks, clipId) {
                    it.copy(stabilization = it.stabilization.copy(enabled = true, analysisReady = true), stabilizationKeyframes = frames)
                } }
            } catch (error: Exception) {
                _analysisError.value = error.message ?: "Stabilization analysis failed"
            } finally {
                _stabilizationRunning.value = false
            }
        }
    }

    fun removeImageBackground(clipId: String) {
        if (_backgroundRemovalRunning.value) return
        val clip = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId } ?: return
        if (!clip.isStillImage) {
            _backgroundRemovalError.value = "Background removal currently supports still-image clips."
            return
        }
        viewModelScope.launch {
            _backgroundRemovalRunning.value = true
            _backgroundRemovalError.value = null
            try {
                val outputUri = withContext(Dispatchers.IO) { backgroundRemovalEngine.removeFromImage(clip.source) }
                edit { tracks -> updateClip(tracks, clipId) {
                    it.copy(
                        source = outputUri,
                        sourceStartMs = 0L,
                        sourceEndMs = it.durationMs,
                        sourceDurationMs = it.durationMs,
                        speed = 1f,
                        speedCurve = emptyList()
                    )
                } }
            } catch (error: Exception) {
                _backgroundRemovalError.value = error.message ?: "Background removal failed"
            } finally {
                _backgroundRemovalRunning.value = false
            }
        }
    }

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    fun updateFreezeFrame(clipId: String, value: FreezeFrameSettings) = edit { tracks -> updateClip(tracks, clipId) { it.copy(freezeFrame = value) } }

    fun insertFreezeFrame(clipId: String, atTimelineMs: Long, durationMs: Long) {
        if (_freezeFrameRunning.value) return
        val trackAndClip = _tracks.value.firstNotNullOfOrNull { track ->
            track.clips.firstOrNull { it.id == clipId }?.let { track to it }
        } ?: return
        val (sourceTrack, clip) = trackAndClip
        if (clip.isStillImage || clip.durationMs < 3L) {
            _freezeFrameError.value = "Choose a video clip and place the playhead inside it."
            return
        }
        if (clip.sourceTrimDurationMs < 3L) {
            _freezeFrameError.value = "The selected trim is too short to split for a freeze frame."
            return
        }
        if (atTimelineMs <= clip.timelineStartMs || atTimelineMs >= clip.timelineEndMs) {
            _freezeFrameError.value = "Move the playhead inside the selected clip first."
            return
        }
        viewModelScope.launch {
            _freezeFrameRunning.value = true
            _freezeFrameError.value = null
            try {
                val localOutputMs = atTimelineMs - clip.timelineStartMs
                val sourceOffsetMs = SpeedCurveMath.sourceOffsetAtOutput(
                    localOutputMs,
                    clip.sourceTrimDurationMs,
                    clip.speedCurve,
                    clip.speed
                ).coerceIn(1L, clip.sourceTrimDurationMs - 1L)
                val sourceTimeMs = clip.sourceStartMs + sourceOffsetMs
                val imageUri = withContext(Dispatchers.IO) {
                    freezeFrameCreator.capture(clip.source, sourceTimeMs)
                }
                edit { tracks ->
                    tracks.map { track ->
                        if (track.id != sourceTrack.id) return@map track
                        val current = track.clips.firstOrNull { it.id == clipId } ?: return@map track
                        val firstCurve = speedCurveSegment(current, 0L, sourceOffsetMs)
                        val secondCurve = speedCurveSegment(current, sourceOffsetMs, current.sourceTrimDurationMs)
                        val first = current.copy(
                            id = UUID.randomUUID().toString(),
                            sourceEndMs = sourceTimeMs,
                            speedCurve = firstCurve,
                            transformKeyframes = current.transformKeyframes.filter { it.timeMs <= sourceOffsetMs },
                            stabilizationKeyframes = current.stabilizationKeyframes.filter { it.timeMs <= sourceOffsetMs }
                        )
                        val holdDuration = durationMs.coerceIn(100L, 5_000L)
                        val frameTimeInClip = sourceOffsetMs
                        val userTransform = KeyframeInterpolator.transformAt(current.transformKeyframes, frameTimeInClip)
                            ?: current.transform
                        val stabilizeTransform = if (current.stabilization.enabled && current.stabilization.analysisReady) {
                            KeyframeInterpolator.transformAt(current.stabilizationKeyframes, frameTimeInClip) ?: com.example.cutline.model.LayerTransform()
                        } else com.example.cutline.model.LayerTransform()
                        val frozenTransform = com.example.cutline.model.LayerTransform(
                            scaleX = userTransform.scaleX * stabilizeTransform.scaleX,
                            scaleY = userTransform.scaleY * stabilizeTransform.scaleY,
                            rotationDegrees = userTransform.rotationDegrees + stabilizeTransform.rotationDegrees,
                            positionX = userTransform.positionX + stabilizeTransform.positionX,
                            positionY = userTransform.positionY + stabilizeTransform.positionY,
                            opacity = userTransform.opacity * stabilizeTransform.opacity
                        )
                        val hold = current.copy(
                            id = UUID.randomUUID().toString(),
                            source = imageUri,
                            timelineStartMs = first.timelineEndMs,
                            sourceStartMs = 0L,
                            sourceEndMs = holdDuration,
                            sourceDurationMs = holdDuration,
                            isStillImage = true,
                            speed = 1f,
                            speedCurve = emptyList(),
                            transform = com.example.cutline.model.LayerTransform(),
                            transformKeyframes = listOf(TransformKeyframe(0L, frozenTransform, KeyframeEasing.LINEAR)),
                            stabilization = StabilizationSettings(),
                            stabilizationKeyframes = emptyList(),
                            audioProcessors = emptyList(),
                            mute = true,
                            freezeFrame = current.freezeFrame.copy(enabled = false, atMs = sourceTimeMs)
                        )
                        val second = current.copy(
                            id = UUID.randomUUID().toString(),
                            timelineStartMs = hold.timelineEndMs,
                            sourceStartMs = sourceTimeMs,
                            speedCurve = secondCurve,
                            transformKeyframes = current.transformKeyframes.filter { it.timeMs >= sourceOffsetMs }
                                .map { it.copy(timeMs = it.timeMs - sourceOffsetMs) },
                            stabilizationKeyframes = current.stabilizationKeyframes.filter { it.timeMs >= sourceOffsetMs }
                                .map { it.copy(timeMs = it.timeMs - sourceOffsetMs) }
                        )
                        val following = track.clips.filter { it.id != clipId }.map { candidate ->
                            if (candidate.timelineStartMs >= current.timelineEndMs) {
                                candidate.copy(timelineStartMs = candidate.timelineStartMs + holdDuration)
                            } else candidate
                        }
                        track.copy(clips = (following + listOf(first, hold, second)).sortedBy { it.timelineStartMs })
                    }
                }
            } catch (error: Exception) {
                _freezeFrameError.value = error.message ?: "Could not insert freeze frame"
            } finally {
                _freezeFrameRunning.value = false
            }
        }
    }

    private fun speedCurveSegment(clip: TimelineClip, startMs: Long, endMs: Long): List<SpeedPoint> {
        if (clip.speedCurve.isEmpty()) return emptyList()
        val start = startMs.coerceIn(0L, clip.sourceTrimDurationMs)
        val end = endMs.coerceIn(start, clip.sourceTrimDurationMs)
        fun valueAt(timeMs: Long) = SpeedCurveMath.speedAt(clip.speedCurve, timeMs, clip.speed)
        return buildList {
            add(SpeedPoint(0L, valueAt(start)))
            clip.speedCurve.filter { it.timeMs > start && it.timeMs < end }
                .forEach { add(SpeedPoint(it.timeMs - start, it.speed)) }
            if (end > start) add(SpeedPoint(end - start, valueAt(end)))
        }
    }

    private fun updateClip(tracks: List<TimelineTrack>, clipId: String, update: (TimelineClip) -> TimelineClip) =
        tracks.map { track -> track.copy(clips = track.clips.map { clip -> if (clip.id == clipId) update(clip) else clip }) }

    fun addTransformKeyframe(clipId: String, timeMs: Long, transform: LayerTransform, easing: KeyframeEasing) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id != clipId) clip else clip.copy(
                transformKeyframes = (clip.transformKeyframes.filterNot { it.timeMs == timeMs } + TransformKeyframe(timeMs, transform, easing))
                    .sortedBy { it.timeMs }
            )
        }) }
    }

    fun removeTransformKeyframe(clipId: String, timeMs: Long) = edit { tracks ->
        tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id == clipId) clip.copy(transformKeyframes = clip.transformKeyframes.filterNot { it.timeMs == timeMs }) else clip
        }) }
    }

    fun copyClipStyle(clipId: String) {
        styleClipboard = _tracks.value.flatMap { it.clips }.firstOrNull { it.id == clipId }
    }

    fun pasteClipStyle(clipId: String) {
        val source = styleClipboard ?: return
        edit { tracks -> tracks.map { track -> track.copy(clips = track.clips.map { clip ->
            if (clip.id != clipId) clip else clip.copy(
                speed = source.speed,
                filter = source.filter,
                colorAdjustments = source.colorAdjustments,
                transform = source.transform,
                textStyle = source.textStyle,
                audioMix = source.audioMix,
                transitionIn = source.transitionIn,
                transitionDurationMs = source.transitionDurationMs
            )
        }) } }
    }
}
