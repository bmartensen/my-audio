package com.martensening.my_audio.ui

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.audio.AirwindowsCatalog
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

/** [original] is Airwindows' own English text, shown unfolded when it differs from [description]. */
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
 * deselecting never touches projects that already use it.
 */
@Composable
fun LibraryContent(library: EffectLibrary, onBack: () -> Unit) {
    val selection by library.selection.collectAsState()
    // Every effect can be heard with its default settings on the bundled sample, also before selecting it.
    val audition = rememberEffectAudition(SampleAuditionSource)
    @Composable
    fun EntryRow(entry: LibraryEntry) = LibraryRow(
        entry = entry,
        selected = entry.id in selection,
        onSelect = { library.setSelected(entry.id, it) },
        playing = audition.playing == entry.id,
        bypassed = audition.bypassed,
        onPlay = { audition.toggle(entry.id, listOf(EffectSlot(EffectChoice(entry.id).create())), emptyList()) },
        onBypass = audition::setBypass
    )
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
    val shownAirwindows = airwindows.filter { it.matches() }

    Scaffold(topBar = { BackTopAppBar(title = stringResource(Res.string.library_title), onBack = onBack) }) { padding ->
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
                    pluralStringResource(Res.plurals.library_count, shownOwn.size + shownAirwindows.size,
                        shownOwn.size + shownAirwindows.size),
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
                if (shownOwn.isEmpty() && shownAirwindows.isEmpty()) {
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
private fun LibraryHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * One effect: name, category and description; a tap unfolds a long description, ▶ plays it on the
 * sample (with the A/B switch while it plays), the box selects it.
 */
@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    playing: Boolean,
    bypassed: Boolean,
    onPlay: () -> Unit,
    onBypass: (Boolean) -> Unit
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
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
                    entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
                val original = entry.original
                if (expanded && original != null && original != entry.description) {
                    Text(
                        formatResource(Res.string.library_original, original),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (playing) Box(Modifier.padding(top = 6.dp)) { AuditionABSwitch(bypassed, onBypass) }
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
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
    }
}
