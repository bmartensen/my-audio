package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import com.martensening.my_audio.platform.uptimeMillis
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.martensening.my_audio.audio.GuitarTuning
import com.martensening.my_audio.data.ChordSheet
import com.martensening.my_audio.data.NotesScroll
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.ui.theme.CheckGreen
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NotesButton(enabled: Boolean, notesVisible: Boolean, onNotes: () -> Unit) {
    // One tap shows or hides the notes; the highlight tells whether they are open.
    IconButton(onClick = onNotes, enabled = enabled) {
        Icon(painterResource(Res.drawable.ic_notes), stringResource(if (notesVisible) Res.string.notes_hide else Res.string.project_notes),
            tint = if (notesVisible) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
}

/** The gear of the project view; shows progress while a track is being imported. */
@Composable
fun ProjectSettingsButton(enabled: Boolean, importing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        if (importing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(painterResource(Res.drawable.ic_settings), stringResource(Res.string.project_settings_title))
    }
}

@Composable
fun ProjectNotes(
    notes: String,
    textSize: Int,
    onTextSize: (Int) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Seconds per text line while [scrolling]; null = no auto-scroll. */
    scrollSecondsPerLine: Float? = null,
    scrolling: Boolean = false,
    /** Changes with every Play or Record start; each run begins at the top of the notes. */
    runId: Int = 0,
    /** True from Play/Record (incl. pre-roll) until Stop. */
    runActive: Boolean = false,
    /** Uptime at which the current run starts moving; null while not known yet (pre-roll). */
    motionStartMillis: Long? = null
) {
    val chords = remember(notes) { ChordSheet.chordRanges(notes) }
    val styled = remember(notes) { highlightChords(notes, chords) }
    val chipColor = chordChipColor()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val verticalScroll = rememberScrollState()
    val lineHeightPx = with(LocalDensity.current) { (textSize * LINE_HEIGHT_FACTOR).sp.toPx() }
    // The speed is set for this text, so every run starts at the top: a defined starting point.
    // The text then rests for the first lines (they are still on screen) and glides afterwards.
    // Notes opened during a run jump to where the run has got to by now. Stop keeps the position.
    fun leadInMillis(secondsPerLine: Float) = (NotesScroll.LEAD_IN_LINES * secondsPerLine * 1000).toLong()
    var syncedRun by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(runId, runActive) {
        val secondsPerLine = scrollSecondsPerLine
        // A finished run never moves the text, e.g. when the notes are opened after Stop.
        if (!runActive || runId == syncedRun || secondsPerLine == null) return@LaunchedEffect
        syncedRun = runId
        val movingMillis = motionStartMillis?.let {
            (uptimeMillis() - it - leadInMillis(secondsPerLine)).coerceAtLeast(0)
        } ?: 0L
        verticalScroll.scrollTo((movingMillis / 1000f / secondsPerLine * lineHeightPx).roundToInt())
    }
    // Teleprompter: glides on every frame while the transport runs; a finger can still adjust
    // the position meanwhile.
    LaunchedEffect(scrolling, scrollSecondsPerLine, lineHeightPx, motionStartMillis) {
        val secondsPerLine = scrollSecondsPerLine ?: return@LaunchedEffect
        if (!scrolling) return@LaunchedEffect
        val pixelsPerNano = lineHeightPx / (secondsPerLine * 1e9f)
        val glideFrom = (motionStartMillis ?: uptimeMillis()) + leadInMillis(secondsPerLine)
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            if (uptimeMillis() >= glideFrom) verticalScroll.dispatchRawDelta((now - last) * pixelsPerNano)
            last = now
        }
    }
    Box(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
        PanelCard(Modifier.fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.project_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onTextSize(textSize - Project.NOTES_TEXT_SIZE_STEP) }, enabled = textSize > Project.MIN_NOTES_TEXT_SIZE) {
                    Icon(painterResource(Res.drawable.ic_zoom_out), stringResource(Res.string.notes_smaller))
                }
                IconButton(onClick = { onTextSize(textSize + Project.NOTES_TEXT_SIZE_STEP) }, enabled = textSize < Project.MAX_NOTES_TEXT_SIZE) {
                    Icon(painterResource(Res.drawable.ic_zoom_in), stringResource(Res.string.notes_larger))
                }
                IconButton(onClick = onEdit) { Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.notes_edit)) }
                IconButton(onClick = onClose) { Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.notes_hide)) }
            }
            // Lines never wrap, so chords stay above their syllables; long lines scroll sideways.
            SelectionContainer(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(verticalScroll)
                    .horizontalScroll(rememberScrollState()).padding(16.dp)
            ) {
                // Empty notes stay empty: a placeholder would look like text that is already there.
                Text(
                    styled,
                    softWrap = false,
                    fontFamily = FontFamily.Monospace, fontSize = textSize.sp, lineHeight = (textSize * LINE_HEIGHT_FACTOR).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    onTextLayout = { layout = it },
                    modifier = Modifier.chordChips({ layout }, chords, chipColor)
                )
            }
        }
    }
}

@Composable
fun NotesEditor(initialText: String, textSize: Int, onSave: suspend (String) -> Boolean, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initialText) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = {
        if (!saving) {
            if (draft != initialText) confirmDiscard = true else onDismiss()
        }
    }, properties = editorDialogProperties()) {
        // The editor ends above the keyboard instead of lying under it, so the lower lines stay
        // reachable while typing; without the keyboard it keeps its usual size.
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            Surface(shape = PanelShape, modifier = Modifier.padding(16.dp).widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(0.95f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(Res.string.notes_edit), style = MaterialTheme.typography.titleLarge)
                    val highlight = remember {
                        VisualTransformation { TransformedText(highlightChords(it.text, ChordSheet.chordRanges(it.text)), OffsetMapping.Identity) }
                    }
                    val chords = remember(draft) { ChordSheet.chordRanges(draft) }
                    val chipColor = chordChipColor()
                    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace, fontSize = textSize.sp, lineHeight = (textSize * LINE_HEIGHT_FACTOR).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Like the notes view: no wrapping; the frame scrolls both ways, so the field itself
                    // never scrolls and the chord chips stay aligned with the text.
                    BoxWithConstraints(
                        Modifier.padding(top = 12.dp).weight(1f).fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, OutlinedTextFieldDefaults.shape)
                            .clip(OutlinedTextFieldDefaults.shape)
                    ) {
                        val viewportWidth = maxWidth
                        val viewportHeight = maxHeight
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            NoSuggestionsKeyboard {
                                BasicTextField(
                                    value = draft, onValueChange = { draft = it; failed = false }, enabled = !saving,
                                    textStyle = textStyle, visualTransformation = highlight,
                                    keyboardOptions = ChordSheetKeyboard,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    onTextLayout = { layout = it },
                                    modifier = Modifier.widthIn(min = viewportWidth).heightIn(min = viewportHeight)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .chordChips({ layout }, chords, chipColor)
                                )
                            }
                        }
                    }
                    if (failed) Text(stringResource(Res.string.notes_save_error), color = MaterialTheme.colorScheme.error)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(Res.string.action_cancel)) }
                        TextButton(enabled = !saving, onClick = {
                            saving = true
                            scope.launch {
                                if (onSave(draft)) onDismiss() else failed = true
                                saving = false
                            }
                        }) { Text(stringResource(Res.string.action_save)) }
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.notes_discard_question)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.notes_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.notes_continue)) } })
    }
}

private val ChordStyle = SpanStyle(fontWeight = FontWeight.Bold)

/**
 * Chord sheets are full of "words" no dictionary knows (Fm, Em/D/C, C#m7): the keyboard must
 * never correct or capitalise them. A normal text keyboard, multi-line, without auto-correct.
 */
private val ChordSheetKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text
)

/** Line height of notes view and editor relative to the font size; one auto-scroll "line". */
private const val LINE_HEIGHT_FACTOR = 1.5f

/** Chords in bold; text and offsets stay unchanged, so editing is unaffected. */
private fun highlightChords(text: String, chords: List<IntRange>) = buildAnnotatedString {
    append(text)
    chords.forEach { addStyle(ChordStyle, it.first, it.last + 1) }
}

/**
 * One chip for chords and progressions alike. In the dark theme the accent tint reads better
 * than the grey; in the light theme the grey from the text colour stays.
 */
@Composable
private fun chordChipColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return if (dark) scheme.primary.copy(alpha = 0.3f) else scheme.onSurface.copy(alpha = 0.12f)
}

/**
 * Draws a rounded chip behind every chord or progression. [layout] is read while drawing, so a
 * new layout only redraws; ranges beyond a layout that has not caught up with the text are skipped.
 */
private fun Modifier.chordChips(layout: () -> TextLayoutResult?, chords: List<IntRange>, color: Color) = drawBehind {
    val result = layout() ?: return@drawBehind
    val length = result.layoutInput.text.length
    val fontSize = result.layoutInput.style.fontSize.toPx()
    val padding = fontSize * 0.15f
    for (range in chords) {
        if (range.last >= length) continue
        val first = result.getBoundingBox(range.first)
        val last = result.getBoundingBox(range.last)
        val centre = (first.top + first.bottom) / 2
        drawRoundRect(
            color,
            topLeft = Offset(first.left - padding, centre - fontSize * 0.65f),
            size = Size(last.right - first.left + 2 * padding, fontSize * 1.3f),
            cornerRadius = CornerRadius(fontSize * 0.2f)
        )
    }
}

@Composable
fun TunerDialog(state: ProjectUiState, onRetry: () -> Unit, onClose: () -> Unit) {
    // null = automatic. String indices run from the low sixth string to the high first string.
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val frequency = state.tunerFrequency
    var previousString by remember { mutableStateOf<Int?>(null) }
    val detected = frequency?.let { GuitarTuning.nearestString(it, previousString) }
    LaunchedEffect(detected) { previousString = detected }
    val target = selected ?: detected
    val cents = if (frequency != null && target != null) GuitarTuning.cents(frequency, target) else null
    val inTune = cents != null && abs(cents) <= 5.0
    var coarse by remember { mutableStateOf(false) }
    LaunchedEffect(cents) {
        if (cents != null) {
            if (abs(cents) > 60) coarse = true
            else if (abs(cents) < 40) coarse = false
        }
    }
    val scale = if (coarse) 300 else 50
    val needle by animateFloatAsState(
        targetValue = ((cents ?: 0.0) / scale).coerceIn(-1.0, 1.0).toFloat(),
        animationSpec = tween(100), label = "tunerNeedle"
    )
    val names = listOf("E", "A", "D", "G", stringResource(Res.string.tuner_b), "E")
    val colour = if (inTune) CheckGreen else MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "microphoneActivity").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "microphonePulse"
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.project_tuner)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.tuner_reference), style = MaterialTheme.typography.bodyMedium)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, maxItemsInEachRow = 3) {
                    names.forEachIndexed { index, name ->
                        FilterChip(
                            selected = target == index, onClick = { selected = if (selected == index) null else index },
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            label = { Text("$name${GuitarTuning.midiNotes[index] / 12 - 1}") }
                        )
                    }
                }
                TextButton(onClick = { selected = null }, enabled = selected != null) {
                    Text(stringResource(if (selected == null) Res.string.tuner_auto_active else Res.string.tuner_auto))
                }
                if (state.tunerError) {
                    Text(stringResource(Res.string.tuner_error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry, enabled = !state.tunerBusy) { Text(stringResource(Res.string.tuner_retry)) }
                } else {
                    Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.tunerListening) {
                            Icon(painterResource(Res.drawable.ic_mic), null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp).alpha(pulse))
                            Text(formatResource(Res.string.tuner_mic_active, state.tunerDeviceName), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        } else {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(stringResource(Res.string.tuner_starting), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    LevelMeter(state.tunerLevel, modifier = Modifier.padding(vertical = 8.dp), meterWidth = 160.dp)
                    Text(target?.let { "${names[it]}${GuitarTuning.midiNotes[it] / 12 - 1}" } ?: "—",
                        style = MaterialTheme.typography.displayLarge, color = colour,
                        modifier = Modifier.height(76.dp).wrapContentHeight(Alignment.CenterVertically))
                    Text(target?.let { formatResource(Res.string.tuner_string, 6 - it, GuitarTuning.frequency(it)) } ?: " ",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        modifier = Modifier.height(24.dp).wrapContentHeight(Alignment.CenterVertically))
                    val outline = MaterialTheme.colorScheme.outlineVariant
                    Canvas(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp)) {
                        val middle = size.width / 2
                        drawLine(outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
                        for (tick in -2..2) {
                            val x = middle + tick * size.width / 4
                            drawLine(outline, Offset(x, 12.dp.toPx()), Offset(x, size.height - 12.dp.toPx()), 2.dp.toPx())
                        }
                        val toleranceWidth = size.width * 5f / scale
                        drawRect(colour.copy(alpha = 0.15f), Offset(middle - toleranceWidth / 2, 0f),
                            androidx.compose.ui.geometry.Size(toleranceWidth, size.height))
                        cents?.let {
                            val x = middle + needle * middle
                            drawLine(colour, Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 4.dp.toPx())
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("−$scale"); Text("0"); Text("+$scale")
                    }
                    Surface(
                        color = if (inTune) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (inTune) Color.White else MaterialTheme.colorScheme.onSurface,
                        shape = PanelShape,
                        border = BorderStroke(if (inTune) 2.dp else 1.dp, if (inTune) CheckGreen else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).height(80.dp)
                    ) {
                        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                            if (inTune) Icon(painterResource(Res.drawable.ic_check_circle), null, Modifier.size(32.dp))
                            else if (cents == null && state.tunerListening) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(when {
                                cents == null && state.tunerLevel >= 0.0004f -> stringResource(Res.string.tuner_searching)
                                cents == null -> stringResource(Res.string.tuner_listen)
                                inTune -> stringResource(Res.string.tuner_in_tune)
                                cents < 0 -> stringResource(Res.string.tuner_low)
                                else -> stringResource(Res.string.tuner_high)
                            }, style = if (cents == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center)
                        }
                    }
                    Text(if (frequency != null && cents != null) formatResource(Res.string.tuner_reading, frequency, cents.roundToInt()) else " ",
                        maxLines = 1, modifier = Modifier.height(24.dp).wrapContentHeight(Alignment.CenterVertically))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) } }
    )
}
