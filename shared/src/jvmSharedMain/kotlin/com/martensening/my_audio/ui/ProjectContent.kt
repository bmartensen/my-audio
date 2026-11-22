package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.martensening.my_audio.audio.InputChannel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LinearProgressIndicator
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.data.Metronome
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.data.RecordTrack
import com.martensening.my_audio.data.Track
import com.martensening.my_audio.ui.theme.RecordRed
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import com.martensening.my_audio.data.PlaybackLoop
import com.martensening.my_audio.data.ProjectArchive
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * The project view shared by Android and desktop. The platform host owns the session lifecycle
 * and supplies what needs platform APIs: microphone permission, file picking and sharing.
 */
@Composable
fun ProjectContent(
    session: ProjectSession,
    projectId: Long,
    autoRecord: Boolean,
    onAutoRecordHandled: () -> Unit,
    onBack: () -> Unit,
    onOpenCalibration: () -> Unit,
    snackbarHostState: SnackbarHostState,
    /** Starts or stops a take once the microphone may be used. */
    onRequestRecording: () -> Unit,
    /** Opens the tuner once the microphone may be used. */
    onRequestTuner: () -> Unit,
    /** Lets the user pick an audio file and passes it to [ProjectSession.importTrack]. */
    onImportTrack: () -> Unit,
    /** Recorded channel of a two-channel input (Android USB interfaces); null hides the switch. */
    inputChannel: InputChannel? = null,
    onInputChannel: (InputChannel) -> Unit = {}
) {
    val viewModel = session
    val state by viewModel.state.collectAsState()
    var deletingTrack by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingRecording by rememberSaveable { mutableStateOf<Long?>(null) }
    var resettingRecordTrack by rememberSaveable { mutableStateOf(false) }
    var editingRecordTrack by rememberSaveable { mutableStateOf(false) }
    var monitorWarning by remember { mutableStateOf<LiveWarning?>(null) }
    var editingMetronome by rememberSaveable { mutableStateOf(false) }
    var envelopeTrack by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTrack by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingMaster by rememberSaveable { mutableStateOf(false) }
    var notesVisible by rememberSaveable(projectId) { mutableStateOf(false) }
    var editingNotes by rememberSaveable(projectId) { mutableStateOf(false) }
    var editingSettings by rememberSaveable { mutableStateOf(false) }
    val notesState = rememberSaveableStateHolder()
    KeepScreenOn(notesVisible || state.tunerOpen)

    LaunchedEffect(Unit) { viewModel.onEnter() }
    LaunchedEffect(autoRecord) {
        if (autoRecord) {
            viewModel.startAutoRecord()
            onAutoRecordHandled()
        }
    }
    // Backgrounding and Activity destruction keep the service-owned session alive.
    val leaveProject = { viewModel.leave(onBack) }
    PlatformBackHandler(onBack = leaveProject)
    PlatformBackHandler(enabled = notesVisible && !editingNotes && !state.tunerOpen) { notesVisible = false }

    val errorText = state.errorMessage?.let { stringResource(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) snackbarHostState.showSnackbar(errorText)
    }

    val requestRecording = onRequestRecording
    val requestTuner = onRequestTuner
    val project = state.project
    val countingDown = state.preRollMillis != null

    project?.tracks?.firstOrNull { it.id == envelopeTrack }?.let { track ->
        VolumeEnvelopeScreen(track, state.waveforms[track.id], project.sampleRate,
            playing = state.mode == TransportMode.Playing, positionFrames = state.positionFrames,
            busy = state.mode == TransportMode.Loading || state.preparingTransport,
            onPreview = { points, frame -> viewModel.previewEnvelope(track, points, frame) },
            onStop = viewModel::stopTransportAsync, onSeek = viewModel::seekTo,
            onSave = { viewModel.finishPreview(track, it) { envelopeTrack = null } },
            onBack = { viewModel.finishPreview(track, null) { envelopeTrack = null } })
        return
    }

    // In landscape the transport moves to a column at the right edge, so tracks keep the height.
    val landscape = LocalWindowInfo.current.containerSize.let { it.width > it.height }
    val transport = @Composable { vertical: Boolean ->
        TransportBar(
            state = state.copy(preparingTransport = state.preparingTransport || state.tunerBusy || state.tunerOpen || state.importing),
            vertical = vertical,
            onPlay = viewModel::togglePlayback,
            onRecord = { if (state.mode == TransportMode.Recording || countingDown) viewModel.toggleRecording() else requestRecording() },
            onMetronome = { editingMetronome = true },
            inputChannel = inputChannel,
            onInputChannel = onInputChannel
        )
    }
    Scaffold(
        topBar = {
            BackTopAppBar(title = project?.name.orEmpty(), onBack = leaveProject) {
                NotesButton(enabled = project != null, notesVisible = notesVisible, onNotes = { notesVisible = !notesVisible })
                ShareMenu(
                    enabled = state.mode == TransportMode.Idle && !state.preparingTransport && !state.exporting && !state.importing && project != null,
                    exporting = state.exporting,
                    mixAvailable = project?.audibleTracks?.isNotEmpty() == true,
                    stemsAvailable = project?.tracks?.isNotEmpty() == true,
                    onShare = viewModel::share
                )
                ProjectSettingsButton(enabled = project != null, importing = state.importing, onClick = { editingSettings = true })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (!landscape) transport(false) }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (project != null) {
                if (notesVisible) {
                    notesState.SaveableStateProvider(projectId) {
                        ProjectNotes(project.notes, project.notesTextSize, onTextSize = viewModel::setNotesTextSize,
                            onEdit = { editingNotes = true }, onClose = { notesVisible = false }, modifier = Modifier.weight(1f),
                            scrollSecondsPerLine = project.notesScroll.secondsPerLine.takeIf { project.notesScroll.enabled },
                            // Moves with the music: during playback, and during a take from project frame 0 on.
                            scrolling = state.mode == TransportMode.Playing ||
                                (state.mode == TransportMode.Recording && state.positionFrames >= 0),
                            runId = state.runId,
                            runActive = countingDown || state.mode == TransportMode.Playing || state.mode == TransportMode.Recording,
                            motionStartMillis = state.motionStartMillis)
                    }
                } else {
                    TrackList(
                        project = project,
                        state = state,
                        onOpenCalibration = { viewModel.leave(onOpenCalibration) },
                        onToggleMute = { viewModel.setMuted(it, !it.muted) },
                        onToggleSolo = { viewModel.setSolo(it, !it.solo) },
                        onLoopChange = viewModel::setLoop,
                        onDelete = { deletingTrack = it.id },
                        onDeleteRecording = { deletingRecording = it.id },
                        onEdit = { editingTrack = it.id },
                        onRecordTrackMute = {
                            // Unmuting the empty record track monitors the microphone: warn where that feeds back or lags.
                            val warning = if (project.nextTake.muted) LiveInputs.current?.warning() else null
                            if (warning != null) monitorWarning = warning
                            else viewModel.setRecordTrackMuted(!project.nextTake.muted)
                        },
                        onRecordTrackSolo = { viewModel.setRecordTrackSolo(!project.nextTake.solo) },
                        onRecordTrackReset = { resettingRecordTrack = true },
                        onRecordTrackEdit = { editingRecordTrack = true },
                        onAddEmptyTrack = viewModel::addEmptyTrack,
                        onEditMaster = { editingMaster = true },
                        onEnvelope = { envelopeTrack = it.id },
                        onSeek = viewModel::seekTo,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (landscape) transport(true)
        }
    }

    if (editingNotes && project != null) {
        NotesEditor(project.notes, project.notesTextSize, onSave = viewModel::saveNotes, onDismiss = { editingNotes = false })
    }
    if (editingSettings && project != null) {
        val toolsEnabled = state.mode == TransportMode.Idle && !state.preparingTransport && !state.exporting &&
            !state.importing && !state.tunerBusy
        ProjectSettingsDialog(
            notesScroll = project.notesScroll,
            preRoll = project.preRoll,
            toolsEnabled = toolsEnabled,
            onImport = {
                editingSettings = false
                onImportTrack()
            },
            onTuner = {
                editingSettings = false
                requestTuner()
            },
            onNotesScroll = viewModel::setNotesScroll,
            onPreRoll = viewModel::setPreRoll,
            onDismiss = { editingSettings = false }
        )
    }
    if (state.tunerOpen) {
        TunerDialog(state, onRetry = viewModel::openTuner, onClose = viewModel::closeTuner)
    }
    project?.tracks?.firstOrNull { it.id == deletingTrack }?.let { track ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.track_delete_question),
            message = formatResource(Res.string.track_delete_info, track.name),
            onConfirm = { viewModel.deleteTrack(track) },
            onDismiss = { deletingTrack = null }
        )
    }
    project?.tracks?.firstOrNull { it.id == deletingRecording }?.let { track ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.track_recording_delete_question),
            message = formatResource(Res.string.track_recording_delete_info, track.name),
            onConfirm = { viewModel.deleteRecording(track) },
            onDismiss = { deletingRecording = null }
        )
    }
    monitorWarning?.let { warning ->
        LiveWarningDialog(
            warning,
            onContinue = {
                monitorWarning = null
                viewModel.setRecordTrackMuted(false)
            },
            onCancel = { monitorWarning = null }
        )
    }
    if (resettingRecordTrack && project != null) {
        ConfirmDeleteDialog(
            title = stringResource(Res.string.track_reset_question),
            message = formatResource(Res.string.track_reset_info, recordTrackName(project)),
            onConfirm = viewModel::resetRecordTrack,
            onDismiss = { resettingRecordTrack = false },
            confirmLabel = Res.string.action_reset
        )
    }
    val librarySelection by viewModel.effectLibrary.selection.collectAsState()
    val effectChoices = remember(librarySelection) { EffectChoice.of(librarySelection) }
    project?.tracks?.firstOrNull { it.id == editingTrack }?.let { track ->
        TrackSettingsDialog(
            track = track,
            choices = effectChoices,
            onSave = viewModel::setTrackSettings,
            onDismiss = { editingTrack = null },
            auditionSource = remember(track.id) { viewModel.trackAuditionSource(track.id) }
        )
    }
    if (editingRecordTrack && project != null) {
        val record = project.nextTake
        val defaultName = formatResource(Res.string.track_default_name, project.nextTrackNumber)
        // The track dialog edits the empty record track like a take without audio: no clean-up rows apply.
        TrackSettingsDialog(
            track = record.asTrack(defaultName),
            choices = effectChoices,
            onSave = { edited -> viewModel.setRecordTrackSettings(record.withSettingsOf(edited, defaultName)) },
            onDismiss = { editingRecordTrack = false }
        )
    }
    if (editingMaster && project != null) {
        MasterSettingsDialog(
            chain = project.masterChain,
            choices = effectChoices,
            onSave = { viewModel.setMasterChain(it) },
            onDismiss = { editingMaster = false },
            auditionSource = remember { viewModel.masterAuditionSource() }
        )
    }
    if (editingMetronome && project != null) {
        MetronomeDialog(
            metronome = project.metronome,
            onChange = viewModel::setMetronome,
            onDismiss = {
                viewModel.saveMetronome()
                editingMetronome = false
            }
        )
    }
}

@Composable
private fun TrackList(
    project: Project,
    state: ProjectUiState,
    onOpenCalibration: () -> Unit,
    onToggleMute: (Track) -> Unit,
    onToggleSolo: (Track) -> Unit,
    onLoopChange: (PlaybackLoop?) -> Unit,
    onDelete: (Track) -> Unit,
    onDeleteRecording: (Track) -> Unit,
    onEdit: (Track) -> Unit,
    onRecordTrackMute: () -> Unit,
    onRecordTrackSolo: () -> Unit,
    onRecordTrackReset: () -> Unit,
    onRecordTrackEdit: () -> Unit,
    onAddEmptyTrack: () -> Unit,
    onEditMaster: () -> Unit,
    onEnvelope: (Track) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier
) {
    // The last track records the next take: the empty record track, or else the last take itself.
    val emptyRecordTrack = project.recordTrack != null || project.tracks.isEmpty()
    val lastTake = project.tracks.lastOrNull()?.takeUnless { emptyRecordTrack }
    val editable = state.mode == TransportMode.Idle && !state.preparingTransport && !state.exporting
    val seek = onSeek.takeIf { state.mode == TransportMode.Playing }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.latencyFrames == null) {
            item { CalibrationHint(onOpenCalibration) }
        }
        val playhead = state.positionFrames.takeIf { state.mode == TransportMode.Playing }
        if (project.tracks.isNotEmpty() && state.mode != TransportMode.Recording) {
            item(key = "loop") { LoopBar(project, playhead, onLoopChange) }
        }
        // Warning, loop, master, tracks: the master stays in view however many tracks follow.
        if (project.tracks.isNotEmpty() && state.mode != TransportMode.Recording && state.preRollMillis == null) {
            item(key = "master") { MasterCard(project.masterChain, editable, onEditMaster) }
        }
        items(project.tracks, key = { it.id }) { track ->
            TrackCard(
                track = track,
                project = project,
                peaks = state.waveforms[track.id],
                playheadFrames = playhead,
                editable = editable,
                onToggleMute = { onToggleMute(track) },
                onToggleSolo = { onToggleSolo(track) },
                // Only the record track loses just its recording; older tracks are deleted as a whole.
                onDelete = { if (track == lastTake) onDeleteRecording(track) else onDelete(track) },
                onEdit = { onEdit(track) },
                onEnvelope = { onEnvelope(track) },
                onSeek = seek
            )
        }
        val preRollMillis = state.preRollMillis
        if (preRollMillis != null) {
            item(key = "preRoll") { PreRollCard(preRollMillis, project.preRoll.seconds) }
        } else if (state.mode == TransportMode.Recording) {
            item(key = "recording") { RecordingCard(project, state) }
        } else if (emptyRecordTrack) {
            item(key = "recordTrack") {
                RecordTrackCard(
                    project = project,
                    editable = editable,
                    onToggleMute = onRecordTrackMute,
                    onToggleSolo = onRecordTrackSolo,
                    onReset = onRecordTrackReset,
                    onEdit = onRecordTrackEdit
                )
            }
        } else {
            item(key = "addTrack") {
                TextButton(onClick = onAddEmptyTrack, enabled = editable) {
                    Icon(painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(Res.string.record_track_add))
                }
            }
        }
    }
}

/** Name the empty record track shows and a take into it gets. */
@Composable
private fun recordTrackName(project: Project) =
    project.nextTake.name ?: formatResource(Res.string.track_default_name, project.nextTrackNumber)

/** The empty record track as the track dialog edits it: a take without audio. */
private fun RecordTrack.asTrack(defaultName: String) = Track(
    id = RECORD_TRACK_ID, name = name ?: defaultName, fileName = "", offsetFrames = 0, lengthFrames = 0,
    volume = volume, muted = muted, solo = solo, pan = pan, createdAtMillis = 0,
    effects = effects, volumeEnvelope = volumeEnvelope
)

/** The settings [edited] in the track dialog, back on the record track; the default name stays a default. */
private fun RecordTrack.withSettingsOf(edited: Track, defaultName: String) = copy(
    name = edited.name.takeUnless { it == defaultName },
    volume = edited.volume, pan = edited.pan, effects = edited.effects
)

private const val RECORD_TRACK_ID = -1L

/**
 * The empty record track at the end: the next take goes here with its settings. A long press opens them; the
 * trash resets them and is disabled while nothing differs from a new track, so its state is visible.
 */
@Composable
private fun RecordTrackCard(
    project: Project,
    editable: Boolean,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit
) {
    val record = project.nextTake
    PanelCard(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(PanelShape)
            .combinedClickable(enabled = editable, onClick = {}, onLongClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recordTrackName(project),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, enabled = editable) {
                    Icon(painterResource(Res.drawable.ic_graphic_eq), contentDescription = stringResource(Res.string.track_effects),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TrackToggle(
                    checked = record.solo,
                    icon = Res.drawable.ic_solo,
                    description = if (record.solo) Res.string.track_unsolo else Res.string.track_solo,
                    enabled = editable,
                    onToggle = onToggleSolo
                )
                TrackToggle(
                    checked = record.muted,
                    icon = if (record.muted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up,
                    description = if (record.muted) Res.string.track_unmute else Res.string.track_mute,
                    enabled = editable,
                    onToggle = onToggleMute
                )
                IconButton(onClick = onReset, enabled = editable && !record.isPristine) {
                    Icon(
                        painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.track_reset_question)
                    )
                }
            }
            ActiveEffectsLine(record.effects.chain)
            val parts = listOf(stringResource(Res.string.record_track_empty)) +
                // Unmuted, the empty record track monitors the microphone while recording.
                listOfNotNull(stringResource(Res.string.record_track_monitoring).takeIf { !record.muted })
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The take being recorded, drawn live on the project timeline from the monitor's peak levels. */
@Composable
private fun RecordingCard(project: Project, state: ProjectUiState) {
    PanelCard(border = BorderStroke(1.dp, RecordRed)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            val countingIn = state.positionFrames < 0 && state.countInFrames > 0
            Text(
                text = if (countingIn) {
                    val framesPerBeat = state.countInFrames / Metronome.COUNT_IN_BEATS
                    val beat = ((state.countInFrames + state.positionFrames) / framesPerBeat + 1)
                        .coerceIn(1, Metronome.COUNT_IN_BEATS.toLong())
                    formatResource(Res.string.project_count_in, beat, Metronome.COUNT_IN_BEATS)
                } else {
                    formatResource(Res.string.project_recording_take, recordTrackName(project))
                },
                style = MaterialTheme.typography.titleMedium,
                color = RecordRed
            )
            val peaks = state.recordingPeaks
            val timeline = maxOf(project.lengthFrames, state.positionFrames, 1L)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(48.dp)
            ) {
                val middle = size.height / 2
                if (peaks.isEmpty() || state.positionFrames <= 0) return@Canvas
                // Each peak covers one monitor interval; spread them over the frames recorded so far.
                val step = size.width * state.positionFrames / timeline / peaks.size
                peaks.forEachIndexed { index, peak ->
                    val x = (index + 0.5f) * step
                    val half = (displayLevel(peak) * middle).coerceAtLeast(0.5f)
                    drawLine(RecordRed, Offset(x, middle - half), Offset(x, middle + half), strokeWidth = (step * 0.7f).coerceAtLeast(1f))
                }
            }
        }
    }
}

/** Silent countdown before the take: big seconds and a bar that runs out. */
@Composable
private fun PreRollCard(remainingMillis: Long, totalSeconds: Int) {
    PanelCard(border = BorderStroke(1.dp, RecordRed)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
        ) {
            Text(stringResource(Res.string.pre_roll_running), style = MaterialTheme.typography.titleMedium, color = RecordRed)
            Text(
                text = preRollSeconds(remainingMillis).toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                color = RecordRed
            )
            LinearProgressIndicator(
                progress = { (remainingMillis / (totalSeconds * 1000f)).coerceIn(0f, 1f) },
                color = RecordRed,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Whole seconds still to wait, counting 5, 4, 3, 2, 1 like a stopwatch countdown. */
private fun preRollSeconds(remainingMillis: Long) = ((remainingMillis + 999) / 1000).coerceAtLeast(1)

@Composable
private fun CalibrationHint(onOpenCalibration: () -> Unit) {
    PanelCard(border = null, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(stringResource(Res.string.project_calibration_hint), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenCalibration, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.project_calibration_action))
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    project: Project,
    peaks: FloatArray?,
    playheadFrames: Long?,
    editable: Boolean,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onEnvelope: () -> Unit,
    /** Non-null while playing: jump playback to a project frame. */
    onSeek: ((Long) -> Unit)?
) {
    PanelCard(
        modifier = Modifier
            // Long press opens volume and effects of the track.
            .clip(PanelShape)
            .combinedClickable(enabled = editable, onClick = {}, onLongClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (track.volume == 1f) {
                        formatDuration(track.lengthFrames, project.sampleRate)
                    } else {
                        formatResource(Res.string.track_summary_volume,
                            formatDuration(track.lengthFrames, project.sampleRate),
                            formatGainDb(track.volume)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onEnvelope, enabled = editable) {
                    Icon(painterResource(Res.drawable.ic_envelope),
                        contentDescription = stringResource(Res.string.envelope_title),
                        tint = if (track.volumeEnvelope.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                TrackToggle(
                    checked = track.solo,
                    icon = Res.drawable.ic_solo,
                    description = if (track.solo) Res.string.track_unsolo else Res.string.track_solo,
                    enabled = editable,
                    onToggle = onToggleSolo
                )
                TrackToggle(
                    checked = track.muted,
                    icon = if (track.muted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up,
                    description = if (track.muted) Res.string.track_unmute else Res.string.track_mute,
                    enabled = editable,
                    onToggle = onToggleMute
                )
                IconButton(onClick = onDelete, enabled = editable) {
                    Icon(
                        painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.track_delete_question),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ActiveEffectsLine(track.effects.chain)
            if (!track.latencyCalibrated) {
                Text(
                    text = stringResource(Res.string.track_uncalibrated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            TrackWaveform(
                track = track,
                projectFrames = project.lengthFrames,
                peaks = peaks,
                playheadFrames = playheadFrames,
                loop = project.activeLoop,
                color = if (project.isAudible(track)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                onSeek = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .height(48.dp)
            )
        }
    }
}

/**
 * The switched-on effects of a track in one line, comma-separated and cut with "…" when they do not fit,
 * so a track with effects is recognisable at a glance; nothing when there are none.
 */
@Composable
private fun ActiveEffectsLine(chain: List<EffectSlot>) {
    val active = chain.filter { it.audible }
    if (active.isEmpty()) return
    Text(
        text = active.map { it.effect.displayName() }.joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/** The master channel: its effect chain at a glance; a tap or long press opens it. */
@Composable
private fun MasterCard(chain: List<EffectSlot>, editable: Boolean, onEdit: () -> Unit) {
    val active = chain.filter { it.audible }
    PanelCard(
        modifier = Modifier
            .clip(PanelShape)
            .combinedClickable(enabled = editable, onClick = onEdit, onLongClick = onEdit)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                stringResource(Res.string.master_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                text = if (active.isEmpty()) stringResource(Res.string.effect_chain_empty)
                else active.map { it.effect.displayName() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (active.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The whole project as a strip: dragging marks the practice loop and switches it on, a tap
 * removes it, the button switches it on and off. The text always says whether playback loops.
 * Only playback loops; Record always starts at the project start.
 */
@Composable
private fun LoopBar(project: Project, playheadFrames: Long?, onLoopChange: (PlaybackLoop?) -> Unit) {
    val length = project.lengthFrames.coerceAtLeast(1)
    val loop = project.loop
    var draft by remember { mutableStateOf<PlaybackLoop?>(null) }
    val shown = draft ?: loop
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline
    PanelCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            // Same filled circle as an active solo or mute.
            TrackToggle(
                checked = loop?.enabled == true,
                icon = Res.drawable.ic_repeat,
                description = Res.string.loop_toggle,
                enabled = loop != null,
                onToggle = { loop?.let { onLoopChange(it.copy(enabled = !it.enabled)) } }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .pointerInput(length) {
                            fun frameAt(x: Float) = (x.coerceIn(0f, size.width.toFloat()) / size.width * length).roundToLong()
                            var anchor = 0L
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    anchor = frameAt(offset.x)
                                    draft = PlaybackLoop(anchor, anchor)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val frame = frameAt(change.position.x)
                                    draft = PlaybackLoop(minOf(anchor, frame), maxOf(anchor, frame))
                                },
                                onDragEnd = {
                                    draft?.takeIf { it.lengthFrames >= project.sampleRate / 2 }?.let(onLoopChange)
                                    draft = null
                                },
                                onDragCancel = { draft = null }
                            )
                        }
                        .pointerInput(Unit) { detectTapGestures { onLoopChange(null) } }
                ) {
                    shown?.takeIf { it.lengthFrames > 0 }?.let {
                        val color = if (it.enabled) primary else inactive
                        val topLeft = Offset(size.width * it.startFrames / length, 0f)
                        val area = Size(size.width * it.lengthFrames / length, size.height)
                        val corner = CornerRadius(8.dp.toPx())
                        drawRoundRect(color.copy(alpha = 0.2f), topLeft, area, corner)
                        drawRoundRect(color, topLeft, area, corner, style = Stroke(2.dp.toPx()))
                    }
                    playheadFrames?.let { drawPlaybackCursor(it.coerceIn(0, length), 0, length) }
                }
                val looping = shown?.takeIf { it.enabled && it.lengthFrames > 0 }
                Text(
                    text = when {
                        looping != null -> formatResource(Res.string.loop_range,
                            formatTime(looping.startFrames, project.sampleRate),
                            formatTime(looping.endFrames, project.sampleRate)
                        )
                        shown != null -> stringResource(Res.string.loop_off)
                        else -> stringResource(Res.string.loop_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (looping != null) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Share button with its choices: the mix exactly as Play outputs it, the cleaned stems for a DAW
 * or a backup of the whole project.
 */
@Composable
private fun ShareMenu(enabled: Boolean, exporting: Boolean, mixAvailable: Boolean, stemsAvailable: Boolean, onShare: (ProjectArchive.Kind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            if (exporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(Res.drawable.ic_share), contentDescription = stringResource(Res.string.action_share))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                Triple(ProjectArchive.Kind.Mix, Res.string.share_mix, Res.string.share_mix_summary),
                Triple(ProjectArchive.Kind.Stems, Res.string.share_stems, Res.string.share_stems_summary),
                Triple(ProjectArchive.Kind.Backup, Res.string.share_backup, Res.string.share_backup_summary)
            ).forEach { (kind, title, summary) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(title))
                            Text(
                                stringResource(summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    enabled = when (kind) {
                        ProjectArchive.Kind.Mix -> mixAvailable
                        ProjectArchive.Kind.Stems -> stemsAvailable
                        ProjectArchive.Kind.Backup -> true
                    },
                    onClick = {
                        expanded = false
                        onShare(kind)
                    }
                )
            }
        }
    }
}

/**
 * Solo and mute: a switched-on state is a filled circle like the active metronome, so a track
 * that is silenced or isolated is obvious at a glance — also while Play or Record locks the
 * buttons, because that is exactly when it matters which tracks sound.
 */
@Composable
private fun TrackToggle(
    checked: Boolean,
    icon: DrawableResource,
    description: StringResource,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            // A locked active button keeps its full highlight; only inactive ones fade.
            disabledContainerColor = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
            disabledContentColor = if (checked) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

/** Draws the take on the shared project timeline, so aligned takes line up across cards. */
@Composable
private fun TrackWaveform(
    track: Track,
    projectFrames: Long,
    peaks: FloatArray?,
    playheadFrames: Long?,
    loop: PlaybackLoop?,
    color: Color,
    onSeek: ((Long) -> Unit)?,
    modifier: Modifier
) {
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val loopShade = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    // A contrasting colour, so the curve stands out from the waveform without covering it.
    val envelopeColor = MaterialTheme.colorScheme.tertiary
    // While playing, a tap continues playback at that point of the shared timeline.
    val seekModifier = if (onSeek != null && projectFrames > 0) {
        Modifier.pointerInput(projectFrames) {
            detectTapGestures { offset ->
                onSeek((offset.x / size.width * projectFrames).toLong().coerceIn(0L, projectFrames))
            }
        }
    } else Modifier
    Canvas(modifier.then(seekModifier)) {
        val middle = size.height / 2
        if (loop != null && projectFrames > 0) {
            val left = size.width * loop.startFrames / projectFrames
            drawRect(loopShade, Offset(left, 0f), Size(size.width * loop.lengthFrames / projectFrames, size.height))
        }
        drawLine(baseline, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 1f)
        if (peaks == null || peaks.isEmpty() || projectFrames <= 0) return@Canvas
        // A filled, mirrored contour on a dB scale: separate thin bars of the raw level left quiet
        // takes almost invisible. Bins of the count-in (before frame 0) are not drawn.
        val framesPerBin = track.lengthFrames.toFloat() / peaks.size
        val first = peaks.indices.firstOrNull { track.offsetFrames + (it + 0.5f) * framesPerBin >= 0 } ?: return@Canvas
        fun xOf(index: Int) = size.width * (track.offsetFrames + (index + 0.5f) * framesPerBin) / projectFrames
        val area = Path().apply {
            moveTo(xOf(first), middle)
            for (i in first until peaks.size) lineTo(xOf(i), middle - displayLevel(peaks[i]) * middle)
            lineTo(xOf(peaks.size - 1), middle)
            for (i in peaks.size - 1 downTo first) lineTo(xOf(i), middle + displayLevel(peaks[i]) * middle)
            close()
        }
        clipRect {
            drawPath(area, color.copy(alpha = color.alpha * 0.55f))
            drawPath(area, color, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
            // The volume envelope as a subtle line over the take: top = 100 %, bottom = 0 %, held beyond its points.
            val points = track.volumeEnvelope
            if (points.isNotEmpty()) {
                val inset = 2.dp.toPx()
                fun x(frame: Long) = size.width * frame / projectFrames
                fun y(gain: Float) = inset + (1f - gain.coerceIn(0f, 1f)) * (size.height - 2 * inset)
                val start = maxOf(track.offsetFrames, 0L)
                val curve = Path().apply {
                    moveTo(x(start), y(points.first().gain))
                    for (point in points) lineTo(x(point.frame.coerceIn(start, track.endFrames)), y(point.gain))
                    lineTo(x(track.endFrames), y(points.last().gain))
                }
                drawPath(curve, envelopeColor, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
            }
        }
        playheadFrames?.let {
            drawPlaybackCursor(it.coerceIn(0, projectFrames), 0, projectFrames)
        }
    }
}

/**
 * Peak (0..1) on a 48 dB display scale for the track waveforms: quiet takes stay readable,
 * silence and noise below −48 dBFS stay on the baseline.
 */
private fun displayLevel(peak: Float): Float =
    if (peak <= 0f) 0f else ((20f * log10(peak) + WAVEFORM_RANGE_DB) / WAVEFORM_RANGE_DB).coerceIn(0f, 1f)

private const val WAVEFORM_RANGE_DB = 48f

/**
 * Position, input level and Play | Record | Metronome below the tracks. In landscape, [vertical]
 * stacks them in a column at the right edge with Record in the middle.
 */
@Composable
private fun TransportBar(
    state: ProjectUiState,
    vertical: Boolean,
    onPlay: () -> Unit,
    onRecord: () -> Unit,
    onMetronome: () -> Unit,
    inputChannel: InputChannel?,
    onInputChannel: (InputChannel) -> Unit
) {
    val project = state.project
    val sampleRate = project?.sampleRate ?: 48_000
    val preRoll = state.preRollMillis
    val status = @Composable {
        val position = if (state.mode == TransportMode.Idle) project?.lengthFrames ?: 0 else state.positionFrames
        Text(
            // The pre-roll counts whole seconds down in the place of the clock, also with the notes open.
            text = if (preRoll != null) formatResource(Res.string.pre_roll_countdown, preRollSeconds(preRoll)) else formatTime(position, sampleRate),
            style = (if (vertical) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
                .copy(fontFeatureSettings = "tnum"),
            color = if (state.mode == TransportMode.Recording || preRoll != null) RecordRed else MaterialTheme.colorScheme.onSurface
        )
        // With the input switch the row is taller in every mode, so Record never moves.
        Box(modifier = Modifier.height(if (inputChannel != null) 32.dp else 20.dp), contentAlignment = Alignment.Center) {
            if (state.mode == TransportMode.Recording) {
                LevelMeter(level = state.inputLevel, meterWidth = if (vertical) 96.dp else 180.dp)
            } else if (preRoll != null) {
                Text(stringResource(Res.string.pre_roll_running), style = MaterialTheme.typography.labelMedium, color = RecordRed)
            } else if (inputChannel != null) {
                InputChannelSwitch(inputChannel, onInputChannel)
            }
        }
    }
    if (vertical) {
        // Record in the middle, Play and Metronome side by side: fits the height of a phone in landscape.
        // 16 dp from the track list plus 16 dp here mirror the 32 dp to the outer edge.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 8.dp)
        ) {
            status()
            RecordControl(state, onRecord)
            // Without tracks there is no Play: the metronome alone stays centred under Record.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (project?.tracks?.isNotEmpty() == true) PlayButton(state, onPlay)
                MetronomeButton(state, onMetronome)
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            status()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                PlayButton(state, onPlay)
                RecordControl(state, onRecord)
                MetronomeButton(state, onMetronome)
            }
        }
    }
}

/** Compact "1 | 2 | 1+2" choice of the recorded input channel of a two-channel interface. */
@Composable
private fun InputChannelSwitch(selected: InputChannel, onSelect: (InputChannel) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(Res.drawable.ic_mic), contentDescription = stringResource(Res.string.input_channel_title),
            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.height(28.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).selectableGroup()) {
            InputChannel.entries.forEach { channel ->
                val checked = channel == selected
                val description = stringResource(when (channel) {
                    InputChannel.First -> Res.string.input_channel_first
                    InputChannel.Second -> Res.string.input_channel_second
                    InputChannel.Both -> Res.string.input_channel_both
                })
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (channel == InputChannel.Both) 48.dp else 36.dp)
                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .semantics { contentDescription = description }
                        .selectable(checked, role = Role.RadioButton) { onSelect(channel) }
                ) {
                    Text(
                        when (channel) {
                            InputChannel.First -> "1"
                            InputChannel.Second -> "2"
                            InputChannel.Both -> "1+2"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Keeps its place while there is nothing to play, so Record does not move. */
@Composable
private fun PlayButton(state: ProjectUiState, onPlay: () -> Unit) {
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (state.project?.tracks?.isNotEmpty() == true) {
            val playing = state.mode == TransportMode.Playing
            FilledTonalIconButton(
                onClick = onPlay,
                enabled = !state.preparingTransport && (state.mode == TransportMode.Idle || playing),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painterResource(if (playing) Res.drawable.ic_stop else Res.drawable.ic_play),
                    contentDescription = stringResource(if (playing) Res.string.action_stop else Res.string.action_play)
                )
            }
        }
    }
}

@Composable
private fun RecordControl(state: ProjectUiState, onRecord: () -> Unit) {
    // During the pre-roll the button shows Stop: a tap cancels the countdown.
    val countingDown = state.preRollMillis != null
    RecordButton(
        recording = state.mode == TransportMode.Recording || countingDown,
        enabled = countingDown || (!state.preparingTransport && (state.mode == TransportMode.Idle || state.mode == TransportMode.Recording)),
        onClick = onRecord
    )
}

@Composable
private fun MetronomeButton(state: ProjectUiState, onMetronome: () -> Unit) {
    val project = state.project
    // Same 56 dp box as Play, so both side buttons have exactly the same size.
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        FilledTonalIconButton(
            onClick = onMetronome,
            enabled = project != null,
            colors = if (project?.metronome?.enabled == true) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else IconButtonDefaults.filledTonalIconButtonColors(),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(painterResource(Res.drawable.ic_metronome), contentDescription = stringResource(Res.string.metronome_title))
        }
    }
}
