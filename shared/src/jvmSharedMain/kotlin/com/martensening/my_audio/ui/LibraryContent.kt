package com.martensening.my_audio.ui

import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import com.martensening.my_audio.data.ToneLibrary
import com.martensening.my_audio.data.ToneKind
import com.martensening.my_audio.data.ToneImportResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.audio.AirwindowsCatalog
import com.martensening.my_audio.data.ChainEffect
import com.martensening.my_audio.data.EffectLibrary
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Airwindows' categories in the app's languages; unknown ones keep their original name. */
@Composable
fun categoryName(category: String): String = when (category) {
    "Ambience" -> Res.string.aw_category_ambience
    "Amp Sims" -> Res.string.aw_category_amp_sims
    "Bass" -> Res.string.aw_category_bass
    "Biquads" -> Res.string.aw_category_biquads
    "Brightness" -> Res.string.aw_category_brightness
    "Clipping" -> Res.string.aw_category_clipping
    "Consoles" -> Res.string.aw_category_consoles
    "Distortion" -> Res.string.aw_category_distortion
    "Dynamics" -> Res.string.aw_category_dynamics
    "Effects" -> Res.string.aw_category_effects
    "Filter" -> Res.string.aw_category_filter
    "Lo-Fi" -> Res.string.aw_category_lofi
    "Noise" -> Res.string.aw_category_noise
    "Reverb" -> Res.string.aw_category_reverb
    "Saturation" -> Res.string.aw_category_saturation
    "Stereo" -> Res.string.aw_category_stereo
    "Subtlety" -> Res.string.aw_category_subtlety
    "Tape" -> Res.string.aw_category_tape
    "Tone Color" -> Res.string.aw_category_tone_color
    "Unclassified" -> Res.string.aw_category_unclassified
    "Utility" -> Res.string.aw_category_utility
    "XYZ Filters" -> Res.string.aw_category_xyz_filters
    else -> null
}?.let { stringResource(it) } ?: category

private sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Selected : LibraryFilter
    data class Category(val name: String) : LibraryFilter
}

/** [original] is Airwindows' own English text; it stays searchable but is not shown. */
private class LibraryEntry(
    val id: String,
    val name: String,
    val category: String?,
    val description: String,
    val original: String? = null
)

/**
 * The effect library from the home screen: every effect the app can render, searchable and
 * filtered by category. A check box puts an effect into the selection that "Add effect" offers;
 * deselecting never touches projects that already use it. Imported amp models and cabinets follow
 * the own effects; [onImportTones] lets the platform pick files and reports what the import added.
 */
@Composable
fun LibraryContent(
    library: EffectLibrary,
    onBack: () -> Unit,
    tones: ToneLibrary? = ToneLibrary.current,
    onImportTones: (((ToneImportResult?) -> Unit) -> Unit)? = null
) {
    val selection by library.selection.collectAsState()
    val toneList by remember(tones) { tones?.tones ?: MutableStateFlow(emptyList()) }.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var removingTone by remember { mutableStateOf<String?>(null) }
    fun importTones() = onImportTones?.invoke { result ->
        // Imported tones are selected right away, so "Add effect" offers them.
        result?.added?.forEach { library.setSelected(EffectLibrary.toneId(it.hash), true) }
        scope.launch {
            snackbar.showSnackbar(
                if (result == null || result.empty) getString(Res.string.tones_import_none)
                else getString(Res.string.tones_import_result, result.added.size, result.duplicates, result.failed.size)
            )
        }
    }
    // Every effect can be heard on the bundled sample, also before selecting it. One entry at a time
    // unfolds its parameters; they start from the defaults, are heard while playing and never stored.
    val audition = rememberEffectAudition(SampleAuditionSource)
    var expandedId by remember { mutableStateOf<String?>(null) }
    var tuned by remember { mutableStateOf<ChainEffect?>(null) }
    fun expand(id: String) {
        if (expandedId == id) return
        expandedId = id
        tuned = EffectChoice(id).create()
    }
    @Composable
    fun EntryRow(entry: LibraryEntry) = LibraryRow(
        entry = entry,
        expanded = expandedId == entry.id,
        onToggleExpanded = {
            if (expandedId == entry.id) {
                expandedId = null
                tuned = null
                if (audition.playing == entry.id || audition.live == entry.id) audition.stop()
            } else {
                expand(entry.id)
            }
        },
        selected = entry.id in selection,
        onSelect = { library.setSelected(entry.id, it) },
        playing = audition.playing == entry.id,
        onPlay = {
            expand(entry.id)
            val effect = tuned ?: EffectChoice(entry.id).create()
            audition.toggle(entry.id, listOf(EffectSlot(effect)), emptyList())
        },
        controls = {
            tuned?.let { effect ->
                // The same "Listen | Live" buttons as in the track dialog; the header keeps its quick ▶ as well.
                AuditionControls(audition, entry.id, { listOf(EffectSlot(tuned ?: effect)) }, { emptyList() },
                    live = true, modifier = Modifier.padding(bottom = 8.dp))
                EffectControls(effect, showDescription = false) { changed ->
                    tuned = changed
                    audition.update(entry.id, listOf(EffectSlot(changed)), emptyList())
                }
                EffectLibrary.toneHash(entry.id)?.let { hash ->
                    TextButton(onClick = { removingTone = hash }) { Text(stringResource(Res.string.tone_remove)) }
                }
            }
        }
    )
    removingTone?.let { hash ->
        val tone = tones?.find(hash)
        if (tone == null) {
            removingTone = null
        } else {
            ConfirmDeleteDialog(
                title = stringResource(Res.string.tone_remove_question),
                message = formatResource(Res.string.tone_remove_info, tone.name),
                onConfirm = {
                    val id = EffectLibrary.toneId(hash)
                    if (audition.playing == id || audition.live == id) audition.stop()
                    if (expandedId == id) {
                        expandedId = null
                        tuned = null
                    }
                    library.setSelected(id, false)
                    tones.remove(hash)
                    removingTone = null
                },
                onDismiss = { removingTone = null }
            )
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filterKey by rememberSaveable { mutableStateOf("") }
    val categories = remember { AirwindowsCatalog.effects.map { it.category }.distinct().sorted() }
    val filter = when {
        filterKey == "selected" -> LibraryFilter.Selected
        filterKey.startsWith("category:") -> LibraryFilter.Category(filterKey.removePrefix("category:"))
        else -> LibraryFilter.All
    }

    val toneDescription = stringResource(Res.string.library_tone_description)
    val reverbDescription = stringResource(Res.string.library_reverb_description)
    val own = listOf(
        LibraryEntry(EffectLibrary.TONE, effectName(EffectLibrary.TONE), null, toneDescription),
        LibraryEntry(EffectLibrary.REVERB, effectName(EffectLibrary.REVERB), null, reverbDescription)
    )
    val ampLabel = stringResource(Res.string.tone_kind_amp)
    val cabinetLabel = stringResource(Res.string.tone_kind_cabinet)
    val toneEntries = toneList.sortedWith(compareBy({ it.kind }, { it.name.lowercase() })).map { tone ->
        val kind = if (tone.kind == ToneKind.AmpModel) ampLabel else cabinetLabel
        LibraryEntry(EffectLibrary.toneId(tone.hash), tone.name, null,
            listOf(kind, tone.details).filter { it.isNotEmpty() }.joinToString(" · "))
    }
    val descriptions by rememberAirwindowsDescriptions()
    val airwindows = remember(descriptions) {
        AirwindowsCatalog.effects.map {
            LibraryEntry(EffectLibrary.airwindowsId(it.name), it.name, it.category, descriptions[it.name] ?: it.description,
                it.description)
        }
    }
    fun LibraryEntry.matches(): Boolean {
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val text = "$name $description ${original.orEmpty()} ${category.orEmpty()}".lowercase()
        val inFilter = when (filter) {
            LibraryFilter.All -> true
            LibraryFilter.Selected -> id in selection
            is LibraryFilter.Category -> category == filter.name
        }
        return inFilter && words.all { it in text }
    }
    val shownOwn = own.filter { it.matches() }
    val shownTones = toneEntries.filter { it.matches() }
    // The section with its import button stays visible without filter, also while it is still empty.
    val showToneSection = onImportTones != null && (shownTones.isNotEmpty() || (query.isBlank() && filter == LibraryFilter.All))
    val shownAirwindows = airwindows.filter { it.matches() }

    Scaffold(
        topBar = { BackTopAppBar(title = stringResource(Res.string.library_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(modifier = Modifier.widthIn(max = PanelMaxWidth).fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.library_search)) },
                    trailingIcon = if (query.isEmpty()) null else {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(painterResource(Res.drawable.ic_close), contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = filter == LibraryFilter.All, onClick = { filterKey = "" },
                            label = { Text(stringResource(Res.string.library_filter_all)) })
                    }
                    item {
                        FilterChip(selected = filter == LibraryFilter.Selected, onClick = { filterKey = "selected" },
                            label = { Text(stringResource(Res.string.library_filter_selected) + " · " + selection.size) })
                    }
                    items(categories) { category ->
                        FilterChip(
                            selected = filter == LibraryFilter.Category(category),
                            onClick = { filterKey = if (filter == LibraryFilter.Category(category)) "" else "category:$category" },
                            label = { Text(categoryName(category)) }
                        )
                    }
                }
                Text(
                    pluralStringResource(Res.plurals.library_count, shownOwn.size + shownTones.size + shownAirwindows.size,
                        shownOwn.size + shownTones.size + shownAirwindows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = PanelMaxWidth).fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (shownOwn.isNotEmpty()) {
                    item(key = "own") { LibraryHeader(stringResource(Res.string.library_section_own)) }
                    items(shownOwn, key = { it.id }) { entry -> EntryRow(entry) }
                }
                if (showToneSection) {
                    item(key = "tones") {
                        LibraryHeader(stringResource(Res.string.library_section_tones)) {
                            TextButton(onClick = { importTones() }) { Text(stringResource(Res.string.tones_import)) }
                        }
                    }
                    if (toneEntries.isEmpty()) item(key = "tones-hint") {
                        Text(
                            stringResource(Res.string.tones_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(shownTones, key = { it.id }) { entry -> EntryRow(entry) }
                }
                if (shownAirwindows.isNotEmpty()) {
                    item(key = "airwindows") { LibraryHeader(stringResource(Res.string.library_section_airwindows)) }
                    items(shownAirwindows, key = { it.id }) { entry -> EntryRow(entry) }
                    item(key = "credit") {
                        Text(
                            stringResource(Res.string.library_credit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                if (shownOwn.isEmpty() && shownAirwindows.isEmpty() && !showToneSection) {
                    item(key = "empty") {
                        Text(
                            stringResource(Res.string.library_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        if (action != null) Box(modifier = Modifier.padding(end = 4.dp, top = 8.dp)) { action() }
    }
}

/**
 * One effect: name, category and description; a tap unfolds the full description and the effect's
 * [controls], ▶ plays it on the sample (with the A/B switch while it plays), the box selects it.
 */
@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    playing: Boolean,
    onPlay: () -> Unit,
    controls: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onToggleExpanded).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                    entry.category?.let {
                        Text(
                            categoryName(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Text(
                    entry.description.removePrefix(entry.name + " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    painterResource(if (playing) Res.drawable.ic_stop else Res.drawable.ic_play),
                    contentDescription = stringResource(if (playing) Res.string.effect_audition_stop else Res.string.effect_audition_play),
                    tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(checked = selected, onCheckedChange = onSelect)
        }
        if (expanded) Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { controls() }
        HorizontalDivider()
    }
}
