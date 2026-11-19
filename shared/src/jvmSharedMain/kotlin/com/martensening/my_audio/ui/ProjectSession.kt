package com.martensening.my_audio.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.martensening.my_audio.data.VolumePoint
import com.martensening.my_audio.data.VolumeEnvelope
import com.martensening.my_audio.platform.Log
import com.martensening.my_audio.platform.elapsedRealtimeMillis
import com.martensening.my_audio.platform.uptimeMillis
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import com.martensening.my_audio.audio.AudioEngine
import com.martensening.my_audio.audio.TransportRoute
import com.martensening.my_audio.audio.NativeAudio
import kotlinx.coroutines.CancellationException
import com.martensening.my_audio.audio.Waveform
import com.martensening.my_audio.data.Metronome
import com.martensening.my_audio.data.NotesScroll
import com.martensening.my_audio.data.PreRoll
import com.martensening.my_audio.data.PlaybackLoop
import com.martensening.my_audio.data.ProjectArchive
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.Track
import com.martensening.my_audio.data.TrackEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class TransportMode { Loading, Idle, Playing, Recording }

/** A finished export to hand out (Android share sheet, desktop save dialog). */
data class ShareRequest(val file: File, val mimeType: String)

data class ProjectUiState(
    val project: Project? = null,
    val mode: TransportMode = TransportMode.Loading,
    val positionFrames: Long = 0,
    val inputLevel: Float = 0f,
    /** Live peaks of the running take, one per monitor interval; replaced by the file waveform afterwards. */
    val recordingPeaks: List<Float> = emptyList(),
    val waveforms: Map<Long, FloatArray> = emptyMap(),
    /** Calibrated latency in project frames; null until the current device pair is calibrated. */
    val latencyFrames: Long? = null,
    /** Count-in of the running take; the position runs from -countInFrames to 0 while counting in. */
    val countInFrames: Long = 0,
    val errorMessage: StringResource? = null,
    /** True while the shared audio file is being rendered and encoded. */
    val exporting: Boolean = false,
    val preparingTransport: Boolean = false,
    /** Remaining silent pre-roll before a take starts; null when no countdown runs. */
    val preRollMillis: Long? = null,
    /** True while an MP3/Ogg file is being converted into a new track. */
    val importing: Boolean = false,
    /** Counts Play and Record starts (Record from its pre-roll on); the notes restart at the top for each run. */
    val runId: Int = 0,
    /** Uptime at which the notes of the current run start moving: Play start, or project frame 0 of a take. */
    val motionStartMillis: Long? = null,
    val tunerOpen: Boolean = false,
    val tunerBusy: Boolean = false,
    val tunerFrequency: Double? = null,
    val tunerLevel: Float = 0f,
    val tunerListening: Boolean = false,
    val tunerDeviceName: String = "",
    val tunerTracking: Boolean = false,
    val tunerError: Boolean = false
)

/** Transport state survives Activity recreation; the foreground service retains a running session. */
class ProjectSession(private val platform: ProjectPlatform, val projectId: Long) {
    /** The device's effect selection, offered by "Add effect" in the track and master dialogs. */
    val effectLibrary get() = platform.effectLibrary

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopRequested = false
    private val repository = platform.repository
    private var transportRoute: TransportRoute? = null
    /** Name of the running take, resolved at its start because finishing may not suspend. */
    private var recordingTrackName: String? = null
    private var recordingLatencyFrames = 0L
    /** Background track changes are written one after another, so the last tap wins on disk too. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val _state = MutableStateFlow(ProjectUiState())
    private val _shareRequests = Channel<ShareRequest>(Channel.BUFFERED)

    /** Finished export files the screen hands to the Android share sheet. */
    val shareRequests: Flow<ShareRequest> = _shareRequests.receiveAsFlow()
    val state: StateFlow<ProjectUiState> = _state.asStateFlow()

    private var monitor: Job? = null
    private var tunerJob: Job? = null
    private var recordingFile: File? = null
    private var recordingMetronome: Metronome? = null
    private var recordingCountIn = 0L
    private var autoRecordHandled = false
    // Transport changes suspend; these main-thread flags ignore taps while one is in progress.
    private var starting = false
    private var stopping = false
    private var finishingPreview = false

    init {
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) {
                // A take interrupted by process death leaves files without a project entry.
                repository.deleteOrphanedFiles(projectId)
                repository.load(projectId)
            }
            if (project == null) {
                _state.update { it.copy(errorMessage = Res.string.project_error_missing) }
                return@launch
            }
            _state.update { it.copy(project = project, mode = TransportMode.Idle, latencyFrames = loadLatencyFrames()) }
            loadWaveforms(project)
            AudioEngine.prepare(project, repository)
        }
    }

    /**
     * Called whenever the screen is shown again: picks up a new calibration and changes made on
     * the home screen (the view model outlives the screen).
     */
    fun onEnter() {
        _state.update { it.copy(latencyFrames = loadLatencyFrames(),
            errorMessage = it.errorMessage.takeUnless { message -> message == Res.string.audio_calibration_required }) }
        if (_state.value.mode != TransportMode.Idle) return
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { repository.load(projectId) } ?: return@launch
            _state.update { it.copy(project = project) }
            AudioEngine.prepare(project, repository)
        }
    }

    fun refreshAudioDevices() {
        if (_state.value.mode == TransportMode.Idle) {
            _state.update { it.copy(latencyFrames = loadLatencyFrames()) }
        }
    }

    fun openTuner() {
        val state = _state.value
        if (state.mode != TransportMode.Idle || starting || stopping || state.exporting || state.tunerBusy) return
        _state.update { it.copy(tunerOpen = true, tunerBusy = true, tunerFrequency = null, tunerError = false,
            tunerListening = false, tunerLevel = 0f, tunerTracking = false) }
        tunerJob = viewModelScope.launch {
            try {
                platform.runTuner(onInterrupted = ::closeTuner,
                    onReady = { name -> _state.update { it.copy(tunerListening = true, tunerDeviceName = name) } }
                ) { frequency, level, tracking ->
                    _state.update { it.copy(tunerFrequency = frequency, tunerLevel = level, tunerTracking = tracking) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Tuner capture failed", error)
                _state.update { it.copy(tunerError = true, tunerFrequency = null) }
            } finally {
                _state.update { it.copy(tunerBusy = false, tunerFrequency = null, tunerListening = false, tunerLevel = 0f, tunerTracking = false) }
            }
        }
    }

    fun closeTuner() {
        _state.update { it.copy(tunerOpen = false) }
        tunerJob?.cancel()
    }

    /** Shows at once and is stored in the background in tap order; also while playing or recording. */
    fun setNotesTextSize(size: Int) {
        val clamped = size.coerceIn(Project.MIN_NOTES_TEXT_SIZE, Project.MAX_NOTES_TEXT_SIZE)
        _state.update { state -> state.copy(project = state.project?.copy(notesTextSize = clamped)) }
        viewModelScope.launch(saveDispatcher) { repository.setNotesTextSize(projectId, clamped) }
    }

    /** Auto-scroll of the notes; applies at once, also while playing, so the speed can be tuned by ear. */
    fun setNotesScroll(scroll: NotesScroll) {
        val value = scroll.copy(secondsPerLine = scroll.secondsPerLine
            .coerceIn(NotesScroll.MIN_SECONDS_PER_LINE, NotesScroll.MAX_SECONDS_PER_LINE))
        _state.update { state -> state.copy(project = state.project?.copy(notesScroll = value)) }
        viewModelScope.launch(saveDispatcher) { repository.setNotesScroll(projectId, value) }
    }

    /** Silent countdown before each take; used from the next Record on. */
    fun setPreRoll(preRoll: PreRoll) {
        val value = preRoll.copy(seconds = preRoll.seconds.coerceIn(PreRoll.MIN_SECONDS, PreRoll.MAX_SECONDS))
        _state.update { state -> state.copy(project = state.project?.copy(preRoll = value)) }
        viewModelScope.launch(saveDispatcher) { repository.setPreRoll(projectId, value) }
    }

    /**
     * Converts an MP3 or Ogg file into a new track (mono, 16 bit, 48 kHz) at project frame 0. It
     * has no latency, click or bleed to correct; volume, effects and envelope work as for takes.
     */
    fun importTrack(source: ImportSource) {
        val state = _state.value
        if (state.mode != TransportMode.Idle || starting || stopping || state.exporting || state.importing ||
            state.tunerOpen || state.tunerBusy) return
        _state.update { it.copy(importing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.Default) {
                    val file = repository.newTrackFile(projectId, System.currentTimeMillis())
                    try {
                        val frames = source.decodeTo(file, NativeAudio.SAMPLE_RATE)
                        val current = checkNotNull(repository.load(projectId))
                        val name = source.displayName
                            ?: getString(Res.string.track_default_name, current.nextTrackNumber)
                        val track = Track(
                            id = file.nameWithoutExtension.toLong(),
                            name = name,
                            fileName = file.name,
                            offsetFrames = 0,
                            lengthFrames = frames,
                            channels = com.martensening.my_audio.audio.PcmWavInfo.read(file).channels,
                            createdAtMillis = System.currentTimeMillis(),
                            effects = TrackEffects.IMPORTED
                        )
                        Log.i(TAG, "Imported ${track.name}: $frames frames")
                        checkNotNull(repository.addTrack(projectId, track))
                    } catch (error: Throwable) {
                        file.delete()
                        throw error
                    }
                }
                _state.update { it.copy(project = withLocalSettings(project)) }
                loadWaveforms(project)
                AudioEngine.prepare(project, repository)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Track import failed", error)
                _state.update { it.copy(errorMessage = Res.string.import_track_failed) }
            } finally {
                _state.update { it.copy(importing = false) }
            }
        }
    }

    /** Keeps settings that may still be on their way to disk when a project comes back from storage. */
    private fun withLocalSettings(project: Project): Project {
        val local = _state.value.project ?: return project
        return project.copy(notes = local.notes, notesTextSize = local.notesTextSize,
            notesScroll = local.notesScroll, preRoll = local.preRoll)
    }

    /** Save only the shared text; never replace tracks or rebuild/stop a running mix. */
    suspend fun saveNotes(notes: String): Boolean = try {
        checkNotNull(withContext(saveDispatcher) { repository.setNotes(projectId, notes) })
        _state.update { state -> state.copy(project = state.project?.copy(notes = notes)) }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e(TAG, "Notes save failed", error)
        false
    }

    /** Starts the first take of a project created from the home screen, exactly once. */
    fun startAutoRecord() {
        if (autoRecordHandled) return
        autoRecordHandled = true
        toggleRecording()
    }

    fun togglePlayback() {
        when (_state.value.mode) {
            TransportMode.Playing -> viewModelScope.launch { stopTransport() }
            TransportMode.Idle -> startTransport(recording = false) {
                val project = _state.value.project ?: return@startTransport
                if (project.audibleTracks.isEmpty()) return@startTransport
                AudioEngine.prepare(project, repository)
                AudioEngine.applyMetronome(project.metronome, recording = false)
                // With an active loop, playback starts at the loop instead of the project start.
                val loop = project.activeLoop
                AudioEngine.applyLoop(loop)
                transportRoute = platform.route()
                if (AudioEngine.startPlayback(transportRoute!!)) {
                    val start = loop?.startFrames ?: 0
                    _state.update { it.copy(mode = TransportMode.Playing, positionFrames = start, errorMessage = null,
                        runId = it.runId + 1, motionStartMillis = uptimeMillis()) }
                    startMonitor()
                } else {
                    _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
                }
            }
            else -> Unit
        }
    }

    fun toggleRecording() {
        // A tap during the pre-roll cancels it; nothing has been recorded yet.
        if (_state.value.preRollMillis != null) {
            stopRequested = true
            return
        }
        when (_state.value.mode) {
            TransportMode.Recording -> viewModelScope.launch { stopTransport() }
            TransportMode.Idle, TransportMode.Loading -> startTake()
            TransportMode.Playing -> Unit
        }
    }

    private fun startTake() {
        startTransport(recording = true) {
            // A take started right after creation may arrive before the initial load finished.
            val project = awaitProject() ?: return@startTransport
            val route = platform.route()
            val latency = platform.latencyFrames(route)
            if (latency == null) {
                _state.update { it.copy(latencyFrames = null, errorMessage = Res.string.audio_calibration_required) }
                return@startTransport
            }
            recordingLatencyFrames = latency
            recordingTrackName = getString(Res.string.track_default_name, project.nextTrackNumber)
            transportRoute = route
            _state.update { it.copy(latencyFrames = recordingLatencyFrames) }
            AudioEngine.prepare(project, repository)
            val file = withContext(Dispatchers.IO) { repository.newTrackFile(project.id, System.currentTimeMillis()) }
            AudioEngine.applyMetronome(project.metronome, recording = true)
            // With the metronome, one bar is counted in; its clicks double as removal templates.
            val countIn = if (project.metronome.enabled) project.metronome.countInFrames(NativeAudio.SAMPLE_RATE) else 0L
            // The silent pre-roll runs once everything is prepared, so the take starts right at zero.
            // A new run: the notes go to the top already for the pre-roll, to read the first line.
            _state.update { it.copy(runId = it.runId + 1, motionStartMillis = null) }
            val preRoll = _state.value.project?.preRoll ?: project.preRoll
            if (preRoll.enabled && !countDown(preRoll.seconds)) {
                file.delete()
                return@startTransport
            }
            if (!route.samePorts(platform.route())) {
                file.delete()
                _state.update { it.copy(latencyFrames = null, errorMessage = Res.string.audio_calibration_required) }
                return@startTransport
            }
            if (AudioEngine.startRecording(file, ProjectRepository.referenceFileFor(file), countIn, route)) {
                recordingFile = file
                recordingMetronome = project.metronome.takeIf { it.enabled }
                recordingCountIn = countIn
                _state.update {
                    it.copy(
                        mode = TransportMode.Recording,
                        preRollMillis = null,
                        // The notes move from project frame 0 on, after the count-in.
                        motionStartMillis = uptimeMillis() + countIn * 1000 / NativeAudio.SAMPLE_RATE,
                        positionFrames = -countIn,
                        countInFrames = countIn,
                        recordingPeaks = emptyList(),
                        errorMessage = null
                    )
                }
                startMonitor()
            } else {
                file.delete()
                ProjectRepository.referenceFileFor(file).delete()
                _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
            }
        }
    }

    fun setMuted(track: Track, muted: Boolean) = changeTrackInBackground(track) { it.copy(muted = muted) }

    /** Several tracks can be soloed; mute flags stay as they are. */
    fun setSolo(track: Track, solo: Boolean) = changeTrackInBackground(track) { it.copy(solo = solo) }

    /**
     * Mute and solo show at once; saving and re-rendering run in the background, in tap order,
     * without locking the screen. Play and Record wait for the mix via the engine lock.
     */
    private fun changeTrackInBackground(track: Track, change: (Track) -> Track) {
        if (_state.value.mode != TransportMode.Idle || starting || stopping) return
        val project = _state.value.project ?: return
        val changed = project.tracks.firstOrNull { it.id == track.id }?.let(change) ?: return
        val updated = project.copy(tracks = project.tracks.map { if (it.id == changed.id) changed else it })
        _state.update { it.copy(project = updated) }
        viewModelScope.launch {
            try {
                withContext(saveDispatcher) { repository.updateTrack(projectId, changed) }
                AudioEngine.prepare(updated, repository)
            } catch (error: Exception) {
                Log.e(TAG, "Track update failed", error)
                _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
            }
        }
    }

    /** Applies immediately, also while playing, and is stored with the project. */
    fun setLoop(loop: PlaybackLoop?) {
        val project = _state.value.project ?: return
        val updated = project.copy(loop = loop)
        _state.update { it.copy(project = updated) }
        if (_state.value.mode == TransportMode.Playing) AudioEngine.applyLoop(updated.activeLoop)
        viewModelScope.launch(Dispatchers.IO) { repository.setLoop(projectId, loop) }
    }

    /** Volume, pan and effects are re-rendered into the mix, never written to the take itself. */
    fun setTrackSettings(track: Track) = changeProject { repository.updateTrack(projectId, track) }

    fun setMasterChain(chain: List<EffectSlot>) = changeProject { repository.setMasterChain(projectId, chain) }

    fun deleteTrack(track: Track) = changeProject { repository.deleteTrack(projectId, track.id) }

    /** Tapping a waveform while playing continues playback there; takes are never moved. */
    fun seekTo(frame: Long) {
        if (_state.value.mode != TransportMode.Playing) return
        AudioEngine.seekPlayback(frame)
    }

    /**
     * Exports and requests the share sheet: the mix exactly as Play outputs it (Ogg/Opus), the
     * cleaned stems (ZIP of WAVs) or a project backup (ZIP).
     */
    fun share(kind: ProjectArchive.Kind) {
        val project = _state.value.project ?: return
        val content = if (kind == ProjectArchive.Kind.Mix) project.audibleTracks else project.tracks
        if (_state.value.mode != TransportMode.Idle || _state.value.exporting || _state.value.importing ||
            (kind != ProjectArchive.Kind.Backup && content.isEmpty())) return
        _state.update { it.copy(exporting = true, errorMessage = null) }
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                // Only the latest export is kept; the share target copies what it needs.
                val dir = File(platform.cacheDir, EXPORT_DIR).apply {
                    deleteRecursively()
                    mkdirs()
                }
                File(dir, ProjectArchive.fileName(project.name, kind))
            }
            val exported = runCatching {
                when (kind) {
                    ProjectArchive.Kind.Mix -> AudioEngine.exportPlayback(project, repository, file, platform::encodeMix)
                    ProjectArchive.Kind.Stems -> exportStems(project, file)
                    ProjectArchive.Kind.Backup -> withContext(Dispatchers.IO) { repository.exportBackup(project.id, file) }
                }
            }.onFailure { Log.e(TAG, "Export failed", it) }.getOrDefault(false)
            _state.update {
                it.copy(exporting = false, errorMessage = if (exported) null else Res.string.project_error_export)
            }
            if (exported) _shareRequests.send(ShareRequest(file, kind.mimeType))
        }
    }

    private suspend fun exportStems(project: Project, target: File): Boolean {
        val dir = File(target.parentFile, STEMS_DIR).apply { mkdirs() }
        val names = ProjectArchive.stemNames(project.tracks)
        val files = names.map { File(dir, it) }
        if (!AudioEngine.exportStems(project, repository, files)) return false
        withContext(Dispatchers.IO) {
            ProjectArchive.writeZip(target, names.zip(files))
            dir.deleteRecursively()
        }
        return true
    }

    /** Applies immediately, also while playing or recording; [saveMetronome] stores it with the project. */
    fun setMetronome(metronome: Metronome) {
        val project = _state.value.project ?: return
        _state.update { it.copy(project = project.copy(metronome = metronome)) }
        AudioEngine.applyMetronome(metronome, recording = _state.value.mode == TransportMode.Recording)
    }

    fun saveMetronome() {
        val metronome = _state.value.project?.metronome ?: return
        viewModelScope.launch(Dispatchers.IO) { repository.setMetronome(projectId, metronome) }
    }

    /** Stops playback or saves a running take from the UI, notification or audio-focus callback. */
    fun stopTransportAsync() {
        stopRequested = true
        if (_state.value.mode == TransportMode.Playing || _state.value.mode == TransportMode.Recording) {
            viewModelScope.launch { stopTransport() }
        }
    }

    /** Emergency service teardown still finalises the WAV and registers the take. */
    fun stopNow() {
        stopRequested = true
        monitor?.cancel()
        val frames = AudioEngine.stopNow()
        platform.stopRouting()
        val project = recordingFile?.let { finishTake(it, frames) }
        recordingFile = null
        _state.update { it.copy(project = project ?: it.project, mode = TransportMode.Idle, positionFrames = 0) }
    }

    fun close() { closeTuner(); viewModelScope.cancel() }

    fun leave(onLeft: () -> Unit) {
        closeTuner()
        stopRequested = true
        viewModelScope.launch {
            tunerJob?.join()
            while (starting || stopping) delay(20)
            stopTransport()
            onLeft()
        }
    }

    /** Auditions only this track with the unsaved curve; project settings and files stay intact. */
    fun previewEnvelope(track: Track, points: List<VolumePoint>, frame: Long) {
        if (_state.value.mode != TransportMode.Idle || _state.value.exporting) return
        startTransport(recording = false) {
            val project = _state.value.project ?: return@startTransport
            val solo = track.copy(muted = false, volumeEnvelope = VolumeEnvelope.sanitize(points, track.endFrames))
            AudioEngine.prepare(project.copy(tracks = listOf(solo)), repository)
            AudioEngine.applyMetronome(project.metronome.copy(enabled = false), recording = false)
            AudioEngine.applyLoop(null)
            transportRoute = platform.route()
            if (AudioEngine.startPlayback(transportRoute!!)) {
                AudioEngine.seekPlayback(frame.coerceIn(0, (track.endFrames - 1).coerceAtLeast(0)))
                _state.update { it.copy(mode = TransportMode.Playing, positionFrames = frame, errorMessage = null) }
                startMonitor()
            } else _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
        }
    }

    /** Close/save can arrive during render or audition; finish that first, then restore the mix. */
    fun finishPreview(track: Track, points: List<VolumePoint>?, onDone: () -> Unit) {
        if (finishingPreview) return
        finishingPreview = true
        stopRequested = true
        viewModelScope.launch {
            while (starting || stopping) delay(20)
            stopTransport()
            _state.update { it.copy(mode = TransportMode.Loading) }
            try {
                if (points != null) {
                    val project = withContext(Dispatchers.IO) {
                        repository.updateTrack(projectId, track.copy(volumeEnvelope = VolumeEnvelope.sanitize(points, track.endFrames)))
                    }
                    if (project != null) _state.update { it.copy(project = project) }
                }
                _state.value.project?.let { AudioEngine.prepare(it, repository) }
                onDone()
            } catch (error: Exception) {
                Log.e(TAG, "Cannot finish envelope editor", error)
                _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
            } finally {
                finishingPreview = false
                _state.update { it.copy(mode = TransportMode.Idle) }
            }
        }
    }

    /** Counts [seconds] down in the UI; false if Stop, Record or leaving cancelled it. */
    private suspend fun countDown(seconds: Int): Boolean {
        val end = elapsedRealtimeMillis() + seconds * 1000L
        while (!stopRequested) {
            val remaining = end - elapsedRealtimeMillis()
            if (remaining <= 0) break
            _state.update { it.copy(preRollMillis = remaining) }
            delay(minOf(remaining, PRE_ROLL_TICK_MILLIS))
        }
        return !stopRequested
    }

    private fun startTransport(recording: Boolean, start: suspend () -> Unit) {
        if (_state.value.tunerOpen || _state.value.tunerBusy || _state.value.importing) return
        if (starting || stopping || _state.value.exporting) return
        starting = true
        _state.update { it.copy(preparingTransport = true) }
        stopRequested = false
        viewModelScope.launch {
            try {
                // Start while the Activity is visible, before rendering or opening the microphone.
                if (!platform.beginTransport(this@ProjectSession, recording)) {
                    _state.update { it.copy(errorMessage = Res.string.project_error_background) }
                    return@launch
                }
                // Playback alone never opens a hands-free microphone link.
                val route = platform.route().let { if (recording) it else it.withoutInput() }
                if (!platform.startRouting(route)) {
                    _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
                    return@launch
                }
                if (!stopRequested) start()
                if (stopRequested) stopTransport()
            } catch (error: Exception) {
                Log.e(TAG, "Transport start failed", error)
                if (AudioEngine.isActive) stopTransport()
                _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
            } finally {
                starting = false
                _state.update { it.copy(preparingTransport = false, preRollMillis = null) }
                if (_state.value.mode != TransportMode.Playing && _state.value.mode != TransportMode.Recording) {
                    platform.stopRouting()
                    platform.endTransport()
                }
            }
        }
    }

    private suspend fun awaitProject(): Project? {
        repeat(50) {
            _state.value.project?.let { return it }
            delay(20)
        }
        return _state.value.project
    }

    private fun startMonitor() {
        monitor?.cancel()
        monitor = viewModelScope.launch {
            while (isActive) {
                // Give the native callback the first block after start/seek. Every playhead uses
                // these actual engine positions, never a competing UI timer or optimistic value.
                delay(MONITOR_INTERVAL_MILLIS)
                val route = transportRoute
                val ports = NativeAudio.currentDeviceIds()
                val recording = _state.value.mode == TransportMode.Recording
                if (recording && NativeAudio.transportXruns() > 0) {
                    _state.update { it.copy(errorMessage = Res.string.audio_recording_dropouts) }
                    viewModelScope.launch { stopTransport() }
                    break
                }
                val current = platform.route()
                // Playback only depends on its output; a take also on the calibrated microphone.
                val devicesChanged = route != null && (
                    (if (recording) !route.samePorts(current) else !route.sameOutput(current)) ||
                        (route.hasOutput && route.outputId != ports[0]) ||
                        (recording && (!route.hasInput || route.inputId != ports[1])))
                if (devicesChanged) {
                    _state.update { it.copy(errorMessage = Res.string.audio_route_changed, latencyFrames = null) }
                    viewModelScope.launch { stopTransport() }
                    break
                }
                if (!AudioEngine.isActive) {
                    // Playback reached the end or a stream error ended the take. Stop in a separate
                    // coroutine, because stopTransport() cancels this monitor.
                    viewModelScope.launch { stopTransport() }
                    break
                }
                val level = if (recording) AudioEngine.takeInputPeak() else 0f
                val position = AudioEngine.positionFrames
                _state.update {
                    it.copy(
                        positionFrames = position,
                        inputLevel = level,
                        // The live waveform starts at project frame 0, after the count-in.
                        recordingPeaks = if (recording && position >= 0) it.recordingPeaks + level else it.recordingPeaks
                    )
                }
            }
        }
    }

    /**
     * Auditioning the effects of [trackId] uses the track itself as its chain receives it: the
     * active loop where it overlaps the track, else from the position when the dialog opened, else
     * from the track's start.
     */
    fun trackAuditionSource(trackId: Long): AuditionSource {
        val position = _state.value.positionFrames
        return object : AuditionSource {
            override suspend fun prepare(): AuditionSourceKind? {
                val project = _state.value.project ?: return null
                val track = project.tracks.firstOrNull { it.id == trackId } ?: return null
                val (start, frames) = auditionRange(project, track.offsetFrames.coerceAtLeast(0), track.endFrames, position)
                if (frames <= 0) return null
                return if (AudioEngine.previewTrack(project, track, repository, start, frames)) AuditionSourceKind.Track else null
            }

            override fun onStart() = stopPlaybackForAudition()
        }
    }

    /** Auditioning the master chain uses the sum of the audible tracks before it, chosen like [trackAuditionSource]. */
    fun masterAuditionSource(): AuditionSource {
        val position = _state.value.positionFrames
        return object : AuditionSource {
            override suspend fun prepare(): AuditionSourceKind? {
                val project = _state.value.project ?: return null
                val (start, frames) = auditionRange(project, 0, project.lengthFrames, position)
                if (frames <= 0) return null
                return if (AudioEngine.previewMix(project, repository, start, frames)) AuditionSourceKind.Mix else null
            }

            override fun onStart() = stopPlaybackForAudition()
        }
    }

    /** Start and length of the audition excerpt within [first, end) of the project. */
    private fun auditionRange(project: Project, first: Long, end: Long, position: Long): Pair<Long, Long> {
        val loop = project.activeLoop
        if (loop != null && loop.startFrames < end && loop.endFrames > first) {
            return loop.startFrames to minOf(loop.lengthFrames, MAX_AUDITION_FRAMES)
        }
        val start = if (position >= first && position < end - AUDITION_FRAMES / 2) position else first
        return start to minOf(AUDITION_FRAMES, end - start)
    }

    /** The project playback and an audition would sound on top of each other. */
    private fun stopPlaybackForAudition() {
        if (_state.value.mode == TransportMode.Playing) viewModelScope.launch { stopTransport() }
    }

    private suspend fun stopTransport() {
        val mode = _state.value.mode
        if (stopping || (mode != TransportMode.Playing && mode != TransportMode.Recording)) return
        stopping = true
        try {
            monitor?.cancel()
            monitor = null
            val frames = AudioEngine.stop()
            val file = recordingFile
            recordingFile = null
            val project = if (mode == TransportMode.Recording && file != null) {
                withContext(Dispatchers.IO) { finishTake(file, frames) }
            } else null
            _state.update { state ->
                state.copy(
                    project = project?.let(::withLocalSettings) ?: state.project,
                    mode = TransportMode.Idle,
                    positionFrames = 0,
                    inputLevel = 0f,
                    latencyFrames = loadLatencyFrames(),
                    recordingPeaks = emptyList()
                )
            }
            project?.let {
                loadWaveforms(it)
                AudioEngine.prepare(it, repository)
            }
        } finally {
            stopping = false
            platform.stopRouting()
            platform.endTransport()
        }
    }

    /** Turns a finished recording into a track, or discards it when it is too short to be useful. */
    private fun finishTake(file: File, frames: Long): Project? {
        val countIn = recordingCountIn
        val metronome = recordingMetronome
        recordingCountIn = 0
        recordingMetronome = null
        val reference = ProjectRepository.referenceFileFor(file)
        if (frames - countIn < MIN_TAKE_FRAMES) {
            file.delete()
            reference.delete()
            return null
        }
        val project = repository.load(projectId) ?: return null
        val latency = recordingLatencyFrames
        val track = Track(
            id = file.nameWithoutExtension.toLong(),
            name = recordingTrackName ?: "Track " + project.nextTrackNumber,
            fileName = file.name,
            // The file starts with the count-in, then the round-trip latency, then project frame 0.
            offsetFrames = -latency - countIn,
            lengthFrames = frames,
            createdAtMillis = System.currentTimeMillis(),
            recordedMetronome = metronome,
            countInFrames = countIn,
            // Only written when something was played; a WAV header alone means silence.
            referenceFileName = reference.takeIf { it.length() > WAV_HEADER_BYTES }?.name
        )
        Log.i(TAG, "Saved take ${track.name}: $frames frames, offset ${track.offsetFrames}")
        return repository.addTrack(projectId, track)
    }

    /** Runs [change] while idle and re-renders the mix. False if refused. */
    private fun changeProject(change: () -> Project?): Boolean {
        if (_state.value.mode != TransportMode.Idle || starting || stopping || _state.value.exporting) return false
        _state.update { it.copy(mode = TransportMode.Loading) }
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.IO) { change() } ?: return@launch
                _state.update { it.copy(project = project) }
                loadWaveforms(project)
                AudioEngine.prepare(project, repository)
            } catch (error: Exception) {
                Log.e(TAG, "Project update failed", error)
                _state.update { it.copy(errorMessage = Res.string.project_error_audio) }
            } finally {
                _state.update { it.copy(mode = TransportMode.Idle) }
            }
        }
        return true
    }

    private suspend fun loadWaveforms(project: Project) {
        val known = _state.value.waveforms
        val waveforms = withContext(Dispatchers.IO) {
            project.tracks.associate { track ->
                track.id to (known[track.id] ?: Waveform.peaks(repository.trackFile(project.id, track), WAVEFORM_BINS))
            }
        }
        _state.update { it.copy(waveforms = waveforms) }
    }

    private fun loadLatencyFrames(): Long? = platform.latencyFrames(platform.route())

    companion object {
        private const val TAG = "MyAudioProject"
        private const val MONITOR_INTERVAL_MILLIS = 50L
        private const val PRE_ROLL_TICK_MILLIS = 50L
        private const val MIN_TAKE_FRAMES = NativeAudio.SAMPLE_RATE / 2L
        /** Effect audition: excerpt length, and the longest loop it takes as a whole. */
        private const val AUDITION_FRAMES = NativeAudio.SAMPLE_RATE * 6L
        private const val MAX_AUDITION_FRAMES = NativeAudio.SAMPLE_RATE * 12L
        private const val WAV_HEADER_BYTES = 44L
        private const val EXPORT_DIR = "exports"
        private const val STEMS_DIR = "stems"
        const val WAVEFORM_BINS = 240
    }
}

/** A single prepared project; switching projects only happens after leaving the old transport. */
object ProjectSessions {
    private var current: ProjectSession? = null

    /** The open session, if any; the desktop stops it when the window closes. */
    val active: ProjectSession? get() = current

    fun get(platform: ProjectPlatform, projectId: Long): ProjectSession {
        current?.takeIf { it.projectId == projectId }?.let { return it }
        current?.close()
        return ProjectSession(platform, projectId).also { current = it }
    }
}
