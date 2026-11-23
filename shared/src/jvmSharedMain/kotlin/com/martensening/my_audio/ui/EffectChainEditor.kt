package com.martensening.my_audio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.martensening.my_audio.audio.AirwindowsCatalog
import com.martensening.my_audio.data.ChainEffect
import com.martensening.my_audio.data.EffectLibrary
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.data.EqEffect
import com.martensening.my_audio.data.ReverbEffect
import com.martensening.my_audio.data.TonePreset
import com.martensening.my_audio.data.Tone
import com.martensening.my_audio.data.ToneKind
import com.martensening.my_audio.data.ToneLibrary
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** An effect "Add effect" can insert, by its library id (see [EffectLibrary]). */
data class EffectChoice(val id: String) {
    fun create(): ChainEffect = when (id) {
        EffectLibrary.TONE -> ChainEffect.Tone().with(TonePreset.Neutral)
        EffectLibrary.REVERB -> ChainEffect.Reverb()
        else -> EffectLibrary.toneHash(id)?.let { hash ->
            val tone = ToneLibrary.current?.find(hash)
            when (tone?.kind) {
                ToneKind.Cabinet -> ChainEffect.Cabinet(hash, tone.name)
                else -> ChainEffect.AmpModel(hash, tone?.name.orEmpty())
            }
        } ?: EffectLibrary.airwindowsName(id).let { name ->
            ChainEffect.Airwindows(name.orEmpty(), AirwindowsCatalog.defaults(name.orEmpty()))
        }
    }

    companion object {
        /**
         * The library selection in the order of the library: My Audio first, then imported amp models and cabinets
         * by name, then Airwindows by name.
         */
        fun of(selection: Set<String>, tones: List<Tone> = ToneLibrary.current?.tones?.value.orEmpty()): List<EffectChoice> =
            listOf(EffectLibrary.TONE, EffectLibrary.REVERB).filter { it in selection }.map(::EffectChoice) +
                tones.filter { EffectLibrary.toneId(it.hash) in selection }
                    .sortedWith(compareBy({ it.kind }, { it.name.lowercase() }))
                    .map { EffectChoice(EffectLibrary.toneId(it.hash)) } +
                selection.mapNotNull { id -> EffectLibrary.airwindowsName(id)?.takeIf { AirwindowsCatalog.find(it) != null } }
                    .sortedBy { it.lowercase() }.map { EffectChoice(EffectLibrary.airwindowsId(it)) }
    }
}

@Composable
fun effectName(id: String): String = when (id) {
    EffectLibrary.TONE -> stringResource(Res.string.effect_tone_name)
    EffectLibrary.REVERB -> stringResource(Res.string.effect_reverb_name)
    else -> EffectLibrary.toneHash(id)?.let { ToneLibrary.current?.find(it)?.name }
        ?: EffectLibrary.airwindowsName(id).orEmpty()
}

@Composable
fun ChainEffect.displayName(): String = when (this) {
    is ChainEffect.Tone -> stringResource(Res.string.effect_tone_name)
    is ChainEffect.Reverb -> stringResource(Res.string.effect_reverb_name)
    is ChainEffect.Airwindows -> name
    is ChainEffect.AmpModel -> name.ifEmpty { stringResource(Res.string.tone_kind_amp) }
    is ChainEffect.Cabinet -> name.ifEmpty { stringResource(Res.string.tone_kind_cabinet) }
    is ChainEffect.Unknown -> type.ifEmpty { stringResource(Res.string.effect_unavailable) }
}

/** Short state of a collapsed effect: its preset, or "custom"; Airwindows effects show their category. */
@Composable
private fun ChainEffect.summary(): String = when (this) {
    is ChainEffect.Tone -> stringResource(preset?.let(::tonePresetName) ?: Res.string.reverb_preset_custom)
    is ChainEffect.Reverb -> ReverbEffect.Preset.entries.firstOrNull { reverb == ReverbEffect().with(it) }
        ?.let { stringResource(reverbPresetName(it)) }
        ?: formatResource(Res.string.value_seconds, reverb.seconds)
    is ChainEffect.Airwindows -> AirwindowsCatalog.find(name)?.let { "Airwindows · " + categoryName(it.category) }
        ?: stringResource(Res.string.effect_unavailable)
    is ChainEffect.AmpModel -> if (ToneLibrary.current?.find(tone) == null) stringResource(Res.string.tone_missing)
        else stringResource(Res.string.tone_kind_amp)
    is ChainEffect.Cabinet -> if (ToneLibrary.current?.find(tone) == null) stringResource(Res.string.tone_missing)
        else stringResource(Res.string.tone_kind_cabinet)
    is ChainEffect.Unknown -> stringResource(Res.string.effect_requires_newer)
}

private class KeyedSlot(val key: Int, val slot: EffectSlot)

/**
 * Effect chain of a track or the master, top = first in the signal: up to [EffectSlot.MAX_PER_CHAIN]
 * inserts, each with a drag handle on the left, a check box on the right that bypasses it without
 * losing its settings, and its controls folded out by a tap (one at a time). Reports every change.
 * With an [audition], a folded-out effect can be heard in the chain and compared with and without it.
 */
@Composable
fun EffectChainEditor(
    chain: List<EffectSlot>,
    onChange: (List<EffectSlot>) -> Unit,
    choices: List<EffectChoice>,
    audition: EffectAudition? = null,
    /** Offers Live (microphone through the chain); a track's chain, never the master's. */
    live: Boolean = false
) {
    var nextKey by remember { mutableIntStateOf(chain.size) }
    val slots = remember { chain.mapIndexed { index, slot -> KeyedSlot(index, slot) }.toMutableStateList() }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    // A: the chain with this slot switched on (also if its box is off); B: the chain without it.
    fun auditionWith(key: Int) = slots.map { if (it.key == key) it.slot.copy(enabled = true) else it.slot }
    fun auditionWithout(key: Int) = slots.filter { it.key != key }.map { it.slot }
    fun publish() {
        onChange(slots.map { it.slot })
        listOfNotNull(audition?.playing as? Int, audition?.live as? Int).distinct().forEach { key ->
            if (slots.any { it.key == key }) audition?.update(key, auditionWith(key), auditionWithout(key)) else audition?.stop()
        }
    }
    // Only the folded-out effect can sound; folding it in or dragging stops it.
    LaunchedEffect(expanded) {
        if (audition != null && (audition.playing ?: audition.live) != null &&
            (audition.playing ?: audition.live) != expanded) audition.stop()
    }
    fun replace(key: Int, slot: EffectSlot) {
        val index = slots.indexOfFirst { it.key == key }
        if (index >= 0) slots[index] = KeyedSlot(key, slot)
        publish()
    }

    Column {
        if (slots.isEmpty()) {
            Text(
                stringResource(Res.string.effect_chain_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        for (item in slots) key(item.key) {
            val isDragged = dragging == item.key
            Column(
                modifier = Modifier
                    .onSizeChanged { heights[item.key] = it.height }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) dragOffset else 0f
                        shadowElevation = if (isDragged) 8f else 0f
                    }
            ) {
                SlotHeader(
                    title = item.slot.effect.displayName(),
                    summary = item.slot.effect.summary(),
                    checked = item.slot.enabled,
                    expanded = expanded == item.key,
                    onToggleExpanded = { expanded = if (expanded == item.key) null else item.key },
                    onCheckedChange = { replace(item.key, item.slot.copy(enabled = it)) },
                    onRemove = { removing = item.key },
                    handle = Modifier.pointerInput(item.key) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = item.key
                                dragOffset = 0f
                                expanded = null
                            },
                            onDragEnd = {
                                dragging = null
                                dragOffset = 0f
                                publish()
                            },
                            onDragCancel = {
                                dragging = null
                                dragOffset = 0f
                                publish()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                // Swap with a neighbour once the row passed half of it.
                                val index = slots.indexOfFirst { it.key == item.key }
                                val below = slots.getOrNull(index + 1)
                                val above = slots.getOrNull(index - 1)
                                if (below != null && dragOffset > (heights[below.key] ?: 0) / 2f) {
                                    slots.add(index + 1, slots.removeAt(index))
                                    dragOffset -= heights[below.key] ?: 0
                                } else if (above != null && -dragOffset > (heights[above.key] ?: 0) / 2f) {
                                    slots.add(index - 1, slots.removeAt(index))
                                    dragOffset += heights[above.key] ?: 0
                                }
                            }
                        )
                    }
                )
                if (expanded == item.key) {
                    // Full width, also below the drag handle: parameter sliders need the room on phones.
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        if (audition != null) {
                            AuditionControls(audition, item.key, { auditionWith(item.key) }, { auditionWithout(item.key) },
                                live = live, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        EffectControls(item.slot.effect) { replace(item.key, item.slot.copy(effect = it)) }
                    }
                }
                HorizontalDivider()
            }
        }
        AddEffectButton(full = slots.size >= EffectSlot.MAX_PER_CHAIN, choices = choices) { choice ->
            val key = nextKey++
            slots.add(KeyedSlot(key, EffectSlot(choice.create())))
            expanded = key
            publish()
        }
    }
    slots.firstOrNull { it.key == removing }?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.effect_remove_question),
            message = formatResource(Res.string.effect_remove_info, item.slot.effect.displayName()),
            confirmLabel = Res.string.effect_remove,
            onConfirm = {
                slots.removeAll { it.key == item.key }
                if (expanded == item.key) expanded = null
                publish()
            },
            onDismiss = { removing = null }
        )
    }
}

/** One effect row: handle, name with its state, fold indicator and bypass check box. */
@Composable
private fun SlotHeader(
    title: String,
    summary: String,
    checked: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    handle: Modifier
) {
    val textColor = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = handle.padding(vertical = 12.dp).padding(end = 8.dp)) {
            Icon(
                painterResource(Res.drawable.ic_drag_handle),
                contentDescription = stringResource(Res.string.effect_move),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clickable(onClick = onToggleExpanded).padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = textColor)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                painterResource(Res.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 180f else 0f)
            )
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        // Always in view, also folded: removing must not hide below a long list of parameters.
        IconButton(onClick = onRemove) {
            Icon(
                painterResource(Res.drawable.ic_delete),
                contentDescription = stringResource(Res.string.effect_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddEffectButton(full: Boolean, choices: List<EffectChoice>, onAdd: (EffectChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box {
            OutlinedButton(onClick = { open = true }, enabled = !full && choices.isNotEmpty()) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.effect_add), modifier = Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(effectName(choice.id)) },
                        onClick = {
                            open = false
                            onAdd(choice)
                        }
                    )
                }
            }
        }
        if (full || choices.isEmpty()) {
            Text(
                if (full) pluralStringResource(Res.plurals.effect_chain_full, EffectSlot.MAX_PER_CHAIN, EffectSlot.MAX_PER_CHAIN)
                else stringResource(Res.string.effect_choices_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private fun Float.roundTo(step: Float) = (this / step).roundToInt() * step

/**
 * The parameter controls of one effect, shared by chain slots and the library audition.
 * [showDescription] is off where the Airwindows description is already visible.
 */
@Composable
internal fun EffectControls(effect: ChainEffect, showDescription: Boolean = true, onChange: (ChainEffect) -> Unit) {
    when (effect) {
        is ChainEffect.Tone -> ToneControls(effect, onChange)
        is ChainEffect.Reverb -> ReverbControls(effect, onChange)
        is ChainEffect.Airwindows -> AirwindowsControls(effect, showDescription, onChange)
        is ChainEffect.AmpModel -> AmpModelControls(effect, onChange)
        is ChainEffect.Cabinet -> CabinetControls(effect, onChange)
        // Its parameters belong to a newer version; it can only be bypassed, moved or removed here.
        is ChainEffect.Unknown -> Text(
            stringResource(Res.string.effect_requires_newer_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** „EQ (My Audio)“: one preset sets EQ and compressor together; each part keeps its own switch. */
@Composable
private fun ToneControls(tone: ChainEffect.Tone, onChange: (ChainEffect.Tone) -> Unit) {
    PresetDropdown(
        label = stringResource(Res.string.effect_tone_preset),
        selected = tone.preset,
        presets = TonePreset.entries,
        name = ::tonePresetName,
        enabled = true,
        onSelect = { onChange(tone.with(it)) }
    )
    CheckRow(Res.string.effect_eq, tone.eq.enabled) { onChange(tone.copy(eq = tone.eq.copy(enabled = it))) }
    listOf(
        Triple(Res.string.effect_eq_low, tone.eq.lowDb) { value: Float -> tone.eq.copy(lowDb = value) },
        Triple(Res.string.effect_eq_presence, tone.eq.presenceDb) { value: Float -> tone.eq.copy(presenceDb = value) },
        Triple(Res.string.effect_eq_high, tone.eq.highDb) { value: Float -> tone.eq.copy(highDb = value) }
    ).forEach { (label, value, set) ->
        ValueSlider(
            label = stringResource(label),
            valueText = formatResource(Res.string.value_db_signed, value),
            value = value,
            range = -EqEffect.RANGE_DB..EqEffect.RANGE_DB,
            enabled = tone.eq.enabled,
            onChange = { onChange(tone.copy(eq = set(it.roundTo(0.5f)))) }
        )
    }
    CheckRow(Res.string.effect_compressor, tone.compressor.enabled) {
        onChange(tone.copy(compressor = tone.compressor.copy(enabled = it)))
    }
    ValueSlider(
        label = stringResource(Res.string.effect_compressor_amount),
        valueText = formatResource(Res.string.value_percent, (tone.compressor.amount * 100).roundToInt()),
        value = tone.compressor.amount,
        range = 0f..1f,
        enabled = tone.compressor.enabled,
        onChange = { onChange(tone.copy(compressor = tone.compressor.copy(amount = it.roundTo(0.01f)))) }
    )
}

/** „Hall (My Audio)“: room presets from chamber to cathedral, values stay editable. */
@Composable
private fun ReverbControls(effect: ChainEffect.Reverb, onChange: (ChainEffect.Reverb) -> Unit) {
    val reverb = effect.reverb
    fun set(value: ReverbEffect) = onChange(ChainEffect.Reverb(value))
    PresetDropdown(
        label = stringResource(Res.string.effect_reverb_room),
        selected = ReverbEffect.Preset.entries.firstOrNull { reverb == ReverbEffect().with(it) },
        presets = ReverbEffect.Preset.entries,
        name = ::reverbPresetName,
        detail = { formatResource(Res.string.value_seconds, it.seconds) },
        enabled = true,
        onSelect = { set(reverb.with(it)) }
    )
    ValueSlider(
        label = stringResource(Res.string.effect_reverb_time),
        valueText = formatResource(Res.string.value_seconds, reverb.seconds),
        value = reverb.seconds,
        range = ReverbEffect.MIN_SECONDS..ReverbEffect.MAX_SECONDS,
        onChange = { set(reverb.copy(seconds = it.roundTo(0.1f))) }
    )
    ValueSlider(
        label = stringResource(Res.string.effect_reverb_pre_delay),
        valueText = formatResource(Res.string.value_milliseconds, reverb.preDelayMs.roundToInt()),
        value = reverb.preDelayMs,
        range = 0f..ReverbEffect.MAX_PRE_DELAY_MS,
        onChange = { set(reverb.copy(preDelayMs = it.roundTo(1f))) }
    )
    ValueSlider(
        label = stringResource(Res.string.effect_reverb_damping),
        valueText = formatResource(Res.string.value_percent, (reverb.damping * 100).roundToInt()),
        value = reverb.damping,
        range = 0f..1f,
        onChange = { set(reverb.copy(damping = it.roundTo(0.01f))) }
    )
    ValueSlider(
        label = stringResource(Res.string.effect_reverb_mix),
        valueText = formatResource(Res.string.value_percent, (reverb.mix * 100).roundToInt()),
        value = reverb.mix,
        range = 0f..1f,
        onChange = { set(reverb.copy(mix = it.roundTo(0.01f))) }
    )
}

/**
 * An Airwindows effect: one slider per parameter (0..1, Airwindows' own scale) labelled with the
 * effect's parameter name and its own display text, e.g. "-3.0 dB".
 */
@Composable
private fun AirwindowsControls(effect: ChainEffect.Airwindows, showDescription: Boolean, onChange: (ChainEffect.Airwindows) -> Unit) {
    val names = remember(effect.name) { AirwindowsCatalog.parameterNames(effect.name) }
    if (names.isEmpty()) {
        Text(
            stringResource(Res.string.effect_unavailable_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val values = remember(effect) { AirwindowsCatalog.complete(effect.name, effect.values) }
    val texts = remember(effect) { AirwindowsCatalog.display(effect.name, values) }
    if (showDescription) AirwindowsCatalog.find(effect.name)?.let { known ->
        Text(
            airwindowsDescription(known),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    CheckRow(Res.string.effect_level_match, effect.levelMatch, summary = stringResource(Res.string.effect_level_match_summary)) {
        onChange(effect.copy(levelMatch = it))
    }
    names.forEachIndexed { index, name ->
        ValueSlider(
            label = name,
            valueText = texts.getOrElse(index) { "" },
            value = values[index],
            range = 0f..1f,
            onChange = { value ->
                onChange(effect.copy(values = values.toMutableList().also { it[index] = value.roundTo(0.001f) }))
            }
        )
    }
    TextButton(onClick = { onChange(effect.copy(values = AirwindowsCatalog.defaults(effect.name))) }) {
        Text(stringResource(Res.string.effect_reset))
    }
}

/** Hint for a slot whose imported tone is not in this device's tone library; true when shown. */
@Composable
private fun MissingTone(hash: String): Boolean {
    if (ToneLibrary.current?.find(hash) != null) return false
    Text(
        stringResource(Res.string.tone_missing_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    return true
}

/** An imported amp model: level matching and the gain into and out of the model. */
@Composable
private fun AmpModelControls(effect: ChainEffect.AmpModel, onChange: (ChainEffect.AmpModel) -> Unit) {
    if (MissingTone(effect.tone)) return
    ToneLibrary.current?.find(effect.tone)?.details?.takeIf { it.isNotEmpty() }?.let { details ->
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp))
    }
    CheckRow(Res.string.effect_level_match, effect.levelMatch, summary = stringResource(Res.string.effect_level_match_summary)) {
        onChange(effect.copy(levelMatch = it))
    }
    val range = ChainEffect.AmpModel.GAIN_RANGE_DB
    ValueSlider(
        label = stringResource(Res.string.effect_input_gain),
        valueText = formatResource(Res.string.value_db_signed, effect.inputDb),
        value = effect.inputDb,
        range = -range..range,
        onChange = { onChange(effect.copy(inputDb = it.roundTo(0.5f))) }
    )
    ValueSlider(
        label = stringResource(Res.string.effect_output_gain),
        valueText = formatResource(Res.string.value_db_signed, effect.outputDb),
        value = effect.outputDb,
        range = -range..range,
        onChange = { onChange(effect.copy(outputDb = it.roundTo(0.5f))) }
    )
    TextButton(onClick = { onChange(effect.copy(inputDb = 0f, outputDb = 0f)) }) {
        Text(stringResource(Res.string.effect_reset))
    }
}

/** An imported cabinet impulse response: level matching only. */
@Composable
private fun CabinetControls(effect: ChainEffect.Cabinet, onChange: (ChainEffect.Cabinet) -> Unit) {
    if (MissingTone(effect.tone)) return
    CheckRow(Res.string.effect_level_match, effect.levelMatch, summary = stringResource(Res.string.effect_level_match_summary)) {
        onChange(effect.copy(levelMatch = it))
    }
}
