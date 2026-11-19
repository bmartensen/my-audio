package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.data.NotesScroll
import com.martensening.my_audio.data.PreRoll

/**
 * The project gear: importing a track, the guitar tuner, auto-scroll of the notes and the silent
 * pre-roll before a take. Settings apply and are stored at once, also while playing.
 */
@Composable
fun ProjectSettingsDialog(
    notesScroll: NotesScroll,
    preRoll: PreRoll,
    /** Import and tuner need a stopped transport. */
    toolsEnabled: Boolean,
    onImport: () -> Unit,
    onTuner: () -> Unit,
    onNotesScroll: (NotesScroll) -> Unit,
    onPreRoll: (PreRoll) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.project_settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolRow(Res.drawable.ic_import, Res.string.import_track, Res.string.import_track_summary, toolsEnabled, onImport)
                ToolRow(Res.drawable.ic_tuner, Res.string.project_tuner, Res.string.tuner_summary, toolsEnabled, onTuner)
                HorizontalDivider()
                SwitchRow(
                    title = stringResource(Res.string.notes_scroll),
                    summary = stringResource(Res.string.notes_scroll_summary),
                    checked = notesScroll.enabled,
                    onChecked = { onNotesScroll(notesScroll.copy(enabled = it)) }
                )
                Stepper(
                    value = formatResource(Res.string.notes_scroll_value, notesScroll.secondsPerLine),
                    enabled = notesScroll.enabled,
                    lessDescription = stringResource(Res.string.notes_scroll_faster),
                    moreDescription = stringResource(Res.string.notes_scroll_slower),
                    canLess = notesScroll.secondsPerLine > NotesScroll.MIN_SECONDS_PER_LINE,
                    canMore = notesScroll.secondsPerLine < NotesScroll.MAX_SECONDS_PER_LINE,
                    onLess = { onNotesScroll(notesScroll.copy(secondsPerLine = notesScroll.secondsPerLine - NotesScroll.STEP_SECONDS_PER_LINE)) },
                    onMore = { onNotesScroll(notesScroll.copy(secondsPerLine = notesScroll.secondsPerLine + NotesScroll.STEP_SECONDS_PER_LINE)) }
                )
                HorizontalDivider()
                SwitchRow(
                    title = stringResource(Res.string.pre_roll),
                    summary = stringResource(Res.string.pre_roll_summary),
                    checked = preRoll.enabled,
                    onChecked = { onPreRoll(preRoll.copy(enabled = it)) }
                )
                Stepper(
                    value = formatResource(Res.string.pre_roll_value, preRoll.seconds),
                    enabled = preRoll.enabled,
                    lessDescription = stringResource(Res.string.pre_roll_shorter),
                    moreDescription = stringResource(Res.string.pre_roll_longer),
                    canLess = preRoll.seconds > PreRoll.MIN_SECONDS,
                    canMore = preRoll.seconds < PreRoll.MAX_SECONDS,
                    onLess = { onPreRoll(preRoll.copy(seconds = preRoll.seconds - 1)) },
                    onMore = { onPreRoll(preRoll.copy(seconds = preRoll.seconds + 1)) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) } }
    )
}

@Composable
private fun ToolRow(icon: DrawableResource, title: StringResource, summary: StringResource, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun Stepper(
    value: String,
    enabled: Boolean,
    lessDescription: String,
    moreDescription: String,
    canLess: Boolean,
    canMore: Boolean,
    onLess: () -> Unit,
    onMore: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onLess, enabled = enabled && canLess,
            modifier = Modifier.semantics { contentDescription = lessDescription }) { Text("−") }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        FilledTonalIconButton(onClick = onMore, enabled = enabled && canMore,
            modifier = Modifier.semantics { contentDescription = moreDescription }) { Text("+") }
    }
}
