package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.data.Track
import com.martensening.my_audio.data.VolumeEnvelope
import com.martensening.my_audio.data.VolumePoint
import kotlin.math.roundToLong

/** Zoom steps of the lens buttons: 1×, 2×, 4×, 8×. */
private const val MAX_ZOOM = 8

/** Touch radius of an existing point, so a tap near it selects instead of adding a new one. */
private val PointHitRadius = 48.dp

/** Identity belongs to the gesture, independent of the point's changing time and order. */
private data class EnvelopeNode(val id: Long, val frame: Long, val gain: Float) {
    fun point() = VolumePoint(frame, gain)
}

/**
 * Unsaved envelope of the editor. Gestures read and write it directly, so a drag always works on
 * the newest points. [selectedId] is -1 while nothing is selected.
 */
@Stable
private class EnvelopeDraft(nodes: List<EnvelopeNode>, private var nextId: Long, selectedId: Long) {
    var nodes by mutableStateOf(nodes)
        private set
    var selectedId by mutableLongStateOf(selectedId)

    val selected get() = nodes.firstOrNull { it.id == selectedId }

    fun points() = nodes.map { it.point() }

    /** Adds and selects a point at the nearest frame that no other point uses. */
    fun add(frame: Long, gain: Float, end: Long): EnvelopeNode {
        val used = nodes.map { it.frame }.toHashSet()
        val free = (0..used.size.toLong()).firstNotNullOfOrNull { distance ->
            listOf(frame + distance, frame - distance).firstOrNull { it in 0..end && it !in used }
        } ?: frame
        val node = EnvelopeNode(nextId++, free, gain)
        nodes = (nodes + node).sortedBy { it.frame }
        selectedId = node.id
        return node
    }

    /** Moves a point; it stops one frame before either neighbour and never changes its order. */
    fun move(id: Long, frame: Long, gain: Float, end: Long) {
        val index = nodes.indexOfFirst { it.id == id }
        if (index < 0) return
        val lower = if (index == 0) 0L else nodes[index - 1].frame + 1
        val upper = if (index == nodes.lastIndex) end else nodes[index + 1].frame - 1
        if (lower > upper) return
        nodes = nodes.toMutableList().also {
            it[index] = it[index].copy(frame = frame.coerceIn(lower, upper), gain = gain.coerceIn(0f, 1f))
        }
    }

    fun removeSelected() {
        nodes = nodes.filterNot { it.id == selectedId }
        selectedId = -1
    }

    fun clear() {
        nodes = emptyList()
        selectedId = -1
    }

    companion object {
        val Saver = listSaver<EnvelopeDraft, Any>(
            save = { draft ->
                listOf(draft.nextId, draft.selectedId) + draft.nodes.flatMap { listOf(it.id, it.frame, it.gain) }
            },
            restore = { values ->
                val nodes = values.drop(2).chunked(3).map { EnvelopeNode(it[0] as Long, it[1] as Long, it[2] as Float) }
                EnvelopeDraft(nodes, values[0] as Long, values[1] as Long)
            }
        )
    }
}

@Composable
fun VolumeEnvelopeScreen(
    track: Track,
    peaks: FloatArray?,
    sampleRate: Int,
    playing: Boolean,
    positionFrames: Long,
    busy: Boolean,
    onPreview: (List<VolumePoint>, Long) -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSave: (List<VolumePoint>) -> Unit,
    onBack: () -> Unit
) {
    val end = track.endFrames.coerceAtLeast(1L)
    val savedEnvelope = remember(track.id) { VolumeEnvelope.sanitize(track.volumeEnvelope, end) }
    val draft = rememberSaveable(track.id, saver = EnvelopeDraft.Saver) {
        EnvelopeDraft(savedEnvelope.mapIndexed { index, p -> EnvelopeNode(index.toLong(), p.frame, p.gain) },
            savedEnvelope.size.toLong(), -1L)
    }
    var zoom by rememberSaveable(track.id) { mutableIntStateOf(1) }
    var windowStart by rememberSaveable(track.id) { mutableLongStateOf(0L) }
    var previewFrame by rememberSaveable(track.id) { mutableLongStateOf(0L) }
    var confirmingLeave by rememberSaveable(track.id) { mutableStateOf(false) }

    val hasChanges = draft.points() != savedEnvelope
    val requestBack: () -> Unit = {
        if (!busy) {
            if (hasChanges) confirmingLeave = true else onBack()
        }
    }
    val visible = (end / zoom).coerceAtLeast(1)
    val start = windowStart.coerceIn(0, end - visible)
    val playhead = if (playing) positionFrames else previewFrame
    val stopPreview by rememberUpdatedState({ previewFrame = positionFrames.coerceIn(0, end); onStop() })
    // Keep the last native position when playback ends; never jump back to an older seek target.
    LaunchedEffect(playing, positionFrames) {
        if (playing) previewFrame = positionFrames.coerceIn(0, end)
    }
    val setPosition: (Long) -> Unit = { frame ->
        previewFrame = frame
        if (playing) onSeek(frame)
    }
    // Zoom keeps the selected point, else the playhead, else the middle of the window in view.
    val zoomTo: (Int) -> Unit = { newZoom ->
        val window = start..(start + visible)
        val anchor = listOfNotNull(draft.selected?.frame, playhead).firstOrNull { it in window } ?: (start + visible / 2)
        val newVisible = (end / newZoom).coerceAtLeast(1)
        zoom = newZoom
        windowStart = (anchor - newVisible / 2).coerceIn(0, end - newVisible)
    }

    PlatformBackHandler(onBack = requestBack)
    if (confirmingLeave) {
        UnsavedChangesDialog(
            busy = busy,
            onSave = {
                confirmingLeave = false
                onSave(draft.points())
            },
            onDiscard = {
                confirmingLeave = false
                onBack()
            },
            onDismiss = { confirmingLeave = false }
        )
    }

    Scaffold(
        topBar = {
            BackTopAppBar(stringResource(Res.string.envelope_title), requestBack) {
                TextButton(onClick = { onSave(draft.points()) }, enabled = !busy) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val landscape = maxWidth > maxHeight
            // In landscape the whole card fits: header, axis and overview take about 190 dp.
            val plotHeight = if (landscape) (maxHeight - 190.dp).coerceIn(96.dp, 360.dp)
                else (maxHeight * 0.45f).coerceIn(120.dp, 360.dp)
            val editor = @Composable {
                PanelCard {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text(
                                text = track.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatDuration(track.lengthFrames, sampleRate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EnvelopePlot(
                            draft = draft,
                            track = track,
                            peaks = peaks,
                            end = end,
                            start = start,
                            visible = visible,
                            playhead = playhead,
                            mode = when {
                                playing -> PlotMode.Seek
                                busy -> PlotMode.Locked
                                else -> PlotMode.Edit
                            },
                            onSeek = setPosition,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .height(plotHeight)
                        )
                        TimeAxis(start, visible, end, sampleRate, onPosition = setPosition)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EnvelopeOverview(
                                track = track,
                                peaks = peaks,
                                curve = draft.points(),
                                end = end,
                                start = start,
                                visible = visible,
                                playhead = playhead,
                                onMove = { windowStart = it.coerceIn(0, end - visible) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            )
                            IconButton(onClick = { zoomTo(zoom / 2) }, enabled = zoom > 1) {
                                Icon(painterResource(Res.drawable.ic_zoom_out), stringResource(Res.string.envelope_zoom_out))
                            }
                            IconButton(onClick = { zoomTo(zoom * 2) }, enabled = zoom < MAX_ZOOM) {
                                Icon(painterResource(Res.drawable.ic_zoom_in), stringResource(Res.string.envelope_zoom_in))
                            }
                        }
                    }
                }
            }
            val controls = @Composable { width: Dp? ->
                EnvelopeControls(
                    playing = playing,
                    busy = busy,
                    playhead = playhead,
                    sampleRate = sampleRate,
                    selected = draft.selected,
                    onPlay = { if (playing) stopPreview() else onPreview(draft.points(), previewFrame) },
                    onRemove = draft::removeSelected,
                    // Resets the draft at once: Discard keeps the saved curve, and a saved reset can be undone.
                    onReset = { if (!playing && !busy) draft.clear() },
                    modifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth().widthIn(max = PanelMaxWidth)
                )
            }
            if (landscape) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) { editor() }
                    controls(TransportRailWidth)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    editor()
                    controls(null)
                }
            }
        }
    }
}

private enum class PlotMode { Edit, Seek, Locked }

/** Waveform with the envelope; edits points, or while playing only seeks. */
@Composable
private fun EnvelopePlot(
    draft: EnvelopeDraft,
    track: Track,
    peaks: FloatArray?,
    end: Long,
    start: Long,
    visible: Long,
    playhead: Long,
    mode: PlotMode,
    onSeek: (Long) -> Unit,
    modifier: Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val waveform = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val input = when (mode) {
        PlotMode.Locked -> Modifier
        PlotMode.Seek -> Modifier.pointerInput(start, visible, end) {
            detectTapGestures { offset ->
                onSeek((start + offset.x / size.width * visible).toLong().coerceIn(0, end - 1))
            }
        }
        PlotMode.Edit -> Modifier.pointerInput(draft, start, visible, end) {
            fun frameAt(x: Float) = (start + x.coerceIn(0f, size.width.toFloat()) / size.width * visible)
                .roundToLong().coerceIn(0, end)
            fun gainAt(y: Float) = (1f - y / size.height).coerceIn(0f, 1f)
            fun position(node: EnvelopeNode) =
                Offset((node.frame - start).toFloat() / visible * size.width, (1 - node.gain) * size.height)

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val nearest = draft.nodes.minByOrNull { (position(it) - down.position).getDistance() }
                    ?.takeIf { (position(it) - down.position).getDistance() <= PointHitRadius.toPx() }
                val node = nearest?.also { draft.selectedId = it.id }
                    ?: draft.add(frameAt(down.position.x), gainAt(down.position.y), end)
                // A long press keeps its point: identity and order are fixed for the whole gesture.
                val anchor = position(node) - down.position
                var dragging = false
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        val target = change.position + anchor
                        draft.move(node.id, frameAt(target.x), gainAt(target.y), end)
                    }
                    change.consume()
                } while (event.changes.any { it.pressed })
            }
        }
    }
    Canvas(modifier.then(input)) {
        for (n in 0..4) {
            val y = size.height * n / 4
            drawLine(grid, Offset(0f, y), Offset(size.width, y))
        }
        drawPeaks(track, peaks, start, visible, waveform, heightShare = 0.45f)
        drawEnvelope(draft.points(), start, visible, primary, 3.dp.toPx())
        draft.nodes.forEach { node ->
            if (node.frame in start..(start + visible)) {
                drawCircle(
                    primary,
                    radius = (if (node.id == draft.selectedId) 9.dp else 6.dp).toPx(),
                    center = Offset((node.frame - start).toFloat() / visible * size.width, (1 - node.gain) * size.height)
                )
            }
        }
        drawPlaybackCursor(playhead, start, visible)
    }
}

/** Start and end of the visible window; a tap sets the listening position. */
@Composable
private fun TimeAxis(start: Long, visible: Long, end: Long, sampleRate: Int, onPosition: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
            .height(32.dp)
            .pointerInput(start, visible) {
                detectTapGestures { offset ->
                    onPosition((start + offset.x / size.width * visible).toLong().coerceIn(0, end - 1))
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val style = MaterialTheme.typography.bodySmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(formatTime(start, sampleRate), style = style, color = color)
        Text(stringResource(Res.string.envelope_axis), style = style, color = color)
        Text(formatTime(start + visible, sampleRate), style = style, color = color)
    }
}

/** The whole track with the visible window; a tap centres the window there, dragging moves it. */
@Composable
private fun EnvelopeOverview(
    track: Track,
    peaks: FloatArray?,
    curve: List<VolumePoint>,
    end: Long,
    start: Long,
    visible: Long,
    playhead: Long,
    onMove: (Long) -> Unit,
    modifier: Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val waveform = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val currentStart by rememberUpdatedState(start)
    Canvas(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(end, visible) {
                detectTapGestures { offset -> onMove((offset.x / size.width * end).toLong() - visible / 2) }
            }
            .pointerInput(end, visible) {
                var origin = 0L
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        origin = currentStart
                        dragged = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                        onMove(origin + (dragged / size.width * end).toLong())
                    }
                )
            }
    ) {
        drawPeaks(track, peaks, 0, end, waveform, heightShare = 0.4f)
        drawEnvelope(curve, 0, end, primary, 1.5.dp.toPx())
        if (visible < end) {
            val left = start.toFloat() / end * size.width
            val window = Size(visible.toFloat() / end * size.width, size.height)
            val corner = CornerRadius(8.dp.toPx())
            drawRoundRect(primary.copy(alpha = 0.15f), Offset(left, 0f), window, corner)
            drawRoundRect(primary, Offset(left, 0f), window, corner, style = Stroke(2.dp.toPx()))
        }
        drawPlaybackCursor(playhead, 0, end)
    }
}

/** Solo preview with position, plus actions on the selected point and the whole envelope. */
@Composable
private fun EnvelopeControls(
    playing: Boolean,
    busy: Boolean,
    playhead: Long,
    sampleRate: Int,
    selected: EnvelopeNode?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalIconButton(onClick = onPlay, enabled = !busy, modifier = Modifier.size(56.dp)) {
                Icon(
                    painterResource(if (playing) Res.drawable.ic_stop else Res.drawable.ic_play),
                    contentDescription = stringResource(if (playing) Res.string.action_stop else Res.string.envelope_preview)
                )
            }
            Text(
                text = formatTime(playhead, sampleRate),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum")
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selected?.let {
                    formatResource(Res.string.envelope_point, it.frame.toDouble() / sampleRate, (it.gain * 100).toInt())
                }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, enabled = selected != null && !playing && !busy) {
                Icon(painterResource(Res.drawable.ic_delete), stringResource(Res.string.envelope_remove_point))
            }
        }
        TextButton(onClick = onReset, enabled = !playing && !busy) {
            Text(stringResource(Res.string.envelope_reset))
        }
    }
}

/** Unsaved changes, in the style of the delete confirmation: Save / Discard / Cancel. */
@Composable
private fun UnsavedChangesDialog(busy: Boolean, onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.envelope_unsaved_title)) },
        text = { Text(stringResource(Res.string.envelope_unsaved_message)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onSave) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = onDiscard) {
                    Text(stringResource(Res.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        }
    )
}

/** Track peaks on the frame window [start, start + visible) of the envelope axis. */
private fun DrawScope.drawPeaks(
    track: Track,
    peaks: FloatArray?,
    start: Long,
    visible: Long,
    color: Color,
    heightShare: Float
) {
    if (peaks == null || peaks.isEmpty()) return
    fun frameOf(index: Int) = track.offsetFrames + (index + 0.5) * track.lengthFrames / peaks.size
    fun xOf(index: Int) = ((frameOf(index) - start) / visible * size.width).toFloat()
    // Count-in and latency before the take's start are silent in the mix and not drawn.
    val audible = track.audibleStartFrames
    // One peak beyond each edge, so the contour runs on to the border instead of ending short of it.
    var first = 0
    while (first + 1 < peaks.size && (frameOf(first + 1) < start || frameOf(first) < audible)) first++
    var last = peaks.size - 1
    while (last - 1 >= 0 && frameOf(last - 1) > start + visible) last--
    if (first > last) return
    // Filled mirrored contour instead of single lines: zoomed in, the peaks lie far apart and
    // lines alone would leave the area mostly empty.
    val middle = size.height / 2
    val area = Path().apply {
        moveTo(xOf(first), middle)
        for (i in first..last) lineTo(xOf(i), middle - peaks[i] * size.height * heightShare)
        lineTo(xOf(last), middle)
        for (i in last downTo first) lineTo(xOf(i), middle + peaks[i] * size.height * heightShare)
        close()
    }
    clipRect {
        drawPath(area, color.copy(alpha = color.alpha * 0.45f))
        drawPath(area, color, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawEnvelope(
    curve: List<VolumePoint>,
    start: Long,
    visible: Long,
    color: Color,
    width: Float
) {
    val path = Path()
    for (x in 0..size.width.toInt()) {
        val y = (1 - envelopeValue(curve, start + (x / size.width * visible).toLong())) * size.height
        if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
    }
    drawPath(path, color, style = Stroke(width))
}

private fun envelopeValue(points: List<VolumePoint>, frame: Long): Float {
    if (points.isEmpty()) return 1f
    if (frame <= points.first().frame) return points.first().gain
    val right = points.indexOfFirst { it.frame >= frame }
    if (right < 0) return points.last().gain
    val a = points[right - 1]
    val b = points[right]
    return a.gain + (b.gain - a.gain) * ((frame - a.frame).toDouble() / (b.frame - a.frame)).toFloat()
}
