package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.data.NormalizeEffect
import com.martensening.my_audio.data.ReverbEffect
import com.martensening.my_audio.data.TonePreset
import com.martensening.my_audio.data.Track
import com.martensening.my_audio.data.TrackEffects
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/** Track gain range: silence up to +6 dB. */
private const val MAX_TRACK_GAIN = 2f

/** Formats a linear gain as dB, e.g. "+2 dB", "−6 dB", "−∞ dB". */
fun formatGainDb(gain: Float): String {
    if (gain <= 0.001f) return "−∞ dB"
    val db = (20 * log10(gain)).roundToInt()
    return when {
        db > 0 -> "+$db dB"
        db < 0 -> "−${abs(db)} dB"
        else -> "0 dB"
    }
}

private fun Float.roundTo(step: Float) = (this / step).roundToInt() * step

/** Effect dialogs use the screen width (Material still caps a dialog at 560 dp) so sliders get room on phones. */
private val effectDialogProperties = DialogProperties(usePlatformDefaultWidth = false)
private val effectDialogModifier = Modifier.padding(horizontal = 16.dp)

/** Formats a pan position as "L 40", "Centre" or "R 40". */
@Composable
private fun panText(pan: Float): String {
    val percent = (abs(pan) * 100).roundToInt()
    return when {
        percent == 0 -> stringResource(Res.string.track_pan_center)
        pan < 0 -> formatResource(Res.string.track_pan_left, percent)
        else -> formatResource(Res.string.track_pan_right, percent)
    }
}

/**
 * Per-track settings opened by a long press, top to bottom in signal order: the input stage
 * (clean-up and normalisation), the effect chain and the channel (volume, panorama). Every effect
 * can be switched off while keeping its values. Applied when the mix is rendered.
 */
@Composable
fun TrackSettingsDialog(
    track: Track,
    choices: List<EffectChoice>,
    onSave: (Track) -> Unit,
    onDismiss: () -> Unit,
    auditionSource: AuditionSource = SampleAuditionSource
) {
    val audition = rememberEffectAudition(auditionSource)
    var name by rememberSaveable { mutableStateOf(track.name) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var volume by rememberSaveable { mutableFloatStateOf(track.volume) }
    var pan by rememberSaveable { mutableFloatStateOf(track.pan) }
    var removeMetronome by rememberSaveable { mutableStateOf(track.effects.removeMetronome) }
    var removeBleed by rememberSaveable { mutableStateOf(track.effects.removeBleed) }
    var normalizeOn by rememberSaveable { mutableStateOf(track.effects.normalize.enabled) }
    var normalizeDb by rememberSaveable { mutableFloatStateOf(track.effects.normalize.targetDb) }
    var normalizeExpanded by rememberSaveable { mutableStateOf(false) }
    var chain by remember { mutableStateOf(track.effects.chain) }

    fun currentEffects() = TrackEffects(
        removeMetronome = removeMetronome,
        removeBleed = removeBleed,
        normalize = NormalizeEffect(normalizeOn, normalizeDb),
        chain = chain
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = effectDialogModifier,
        properties = effectDialogProperties,
        title = {
            if (editingName) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { editingName = false }),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editingName = true }) {
                        Icon(painterResource(Res.drawable.ic_edit), contentDescription = stringResource(Res.string.track_rename))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(Res.string.track_section_input, top = 0.dp)
                track.recordedMetronome?.takeIf { it.enabled }?.let { metronome ->
                    CheckRow(
                        title = Res.string.effect_remove_metronome,
                        checked = removeMetronome,
                        summary = formatResource(Res.string.effect_remove_metronome_summary, metronome.bpm),
                        onChange = { removeMetronome = it }
                    )
                }
                if (track.referenceFileName != null) {
                    CheckRow(
                        title = Res.string.effect_remove_bleed,
                        checked = removeBleed,
                        summary = stringResource(Res.string.effect_remove_bleed_summary),
                        onChange = { removeBleed = it }
                    )
                }
                // Normalisation stays first: it sets the input level for the effects.
                CheckRow(
                    title = Res.string.effect_normalize,
                    checked = normalizeOn,
                    summary = formatResource(Res.string.value_dbfs, normalizeDb),
                    expanded = normalizeExpanded,
                    onToggleExpanded = { normalizeExpanded = !normalizeExpanded },
                    onChange = { normalizeOn = it }
                )
                if (normalizeExpanded) {
                    ValueSlider(
                        label = stringResource(Res.string.effect_normalize_target),
                        valueText = formatResource(Res.string.value_dbfs, normalizeDb),
                        value = normalizeDb,
                        range = NormalizeEffect.MIN_TARGET_DB..NormalizeEffect.MAX_TARGET_DB,
                        enabled = normalizeOn,
                        onChange = { normalizeDb = it.roundTo(0.5f) }
                    )
                }

                SectionTitle(Res.string.track_effects)
                EffectChainEditor(chain, onChange = { chain = it }, choices = choices, audition = audition)

                SectionTitle(Res.string.track_section_channel)
                ValueSlider(
                    label = stringResource(Res.string.track_volume),
                    valueText = formatGainDb(volume),
                    value = volume,
                    range = 0f..MAX_TRACK_GAIN,
                    // Snap to unity gain so 0 dB is easy to hit again.
                    onChange = { volume = if (abs(it - 1f) < 0.04f) 1f else it }
                )
                ValueSlider(
                    label = stringResource(if (track.channels == 2) Res.string.track_balance else Res.string.track_pan),
                    valueText = panText(pan),
                    value = pan,
                    range = -1f..1f,
                    // Snap to the centre so it is easy to hit again.
                    onChange = { pan = if (abs(it) < 0.06f) 0f else it.roundTo(0.01f) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(track.copy(name = name.trim().ifEmpty { track.name }, volume = volume, pan = pan, effects = currentEffects()))
                onDismiss()
            }) {
                Text(stringResource(Res.string.action_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

/** Effect chain on the whole mix; the fixed loudness and limiter stage follows it. */
@Composable
fun MasterSettingsDialog(
    chain: List<EffectSlot>,
    choices: List<EffectChoice>,
    onSave: (List<EffectSlot>) -> Unit,
    onDismiss: () -> Unit,
    auditionSource: AuditionSource = SampleAuditionSource
) {
    var edited by remember { mutableStateOf(chain) }
    val audition = rememberEffectAudition(auditionSource)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = effectDialogModifier,
        properties = effectDialogProperties,
        title = { Text(stringResource(Res.string.master_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                EffectChainEditor(edited, onChange = { edited = it }, choices = choices, audition = audition)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(edited)
                onDismiss()
            }) {
                Text(stringResource(Res.string.action_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

internal fun reverbPresetName(preset: ReverbEffect.Preset) = when (preset) {
    ReverbEffect.Preset.Chamber -> Res.string.reverb_preset_chamber
    ReverbEffect.Preset.Room -> Res.string.reverb_preset_room
    ReverbEffect.Preset.Hall -> Res.string.reverb_preset_hall
    ReverbEffect.Preset.Church -> Res.string.reverb_preset_church
    ReverbEffect.Preset.Cathedral -> Res.string.reverb_preset_cathedral
}

internal fun tonePresetName(preset: TonePreset) = when (preset) {
    TonePreset.Vocals -> Res.string.tone_preset_vocals
    TonePreset.Guitar -> Res.string.tone_preset_guitar
    TonePreset.Piano -> Res.string.tone_preset_piano
    TonePreset.Neutral -> Res.string.tone_preset_neutral
}

/** Preset selection as a drop-down; shows [customName] once the values no longer match a preset. */
@Composable
internal fun <T> PresetDropdown(
    label: String,
    selected: T?,
    presets: List<T>,
    name: (T) -> StringResource,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    customName: StringResource = Res.string.reverb_preset_custom,
    detail: @Composable (T) -> String? = { null }
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text(stringResource(selected?.let(name) ?: customName))
                Icon(painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(stringResource(name(preset))) },
                        trailingIcon = detail(preset)?.let { text ->
                            {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelect(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: StringResource, top: Dp = 12.dp) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = top, bottom = 4.dp)
    )
}

/**
 * On/off row with the check box on the right, like the effect rows. With [onToggleExpanded], a tap on
 * the text folds out the controls below.
 */
@Composable
internal fun CheckRow(
    title: StringResource,
    checked: Boolean,
    summary: String? = null,
    expanded: Boolean? = null,
    onToggleExpanded: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .then(if (onToggleExpanded != null) Modifier.clickable(onClick = onToggleExpanded) else Modifier)
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded != null) {
                Icon(
                    painterResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ValueSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = color)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
