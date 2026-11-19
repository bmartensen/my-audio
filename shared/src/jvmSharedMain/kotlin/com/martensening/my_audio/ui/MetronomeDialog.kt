package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.data.Metronome
import kotlin.math.roundToInt

/** Edits the project metronome; every change is audible immediately, also during a take. */
@Composable
fun MetronomeDialog(metronome: Metronome, onChange: (Metronome) -> Unit, onDismiss: () -> Unit) {
    fun setBpm(bpm: Int) = onChange(metronome.copy(bpm = bpm.coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.metronome_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.metronome_enabled), modifier = Modifier.weight(1f))
                    Switch(checked = metronome.enabled, onCheckedChange = { onChange(metronome.copy(enabled = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.metronome_during_playback))
                        Text(
                            stringResource(Res.string.metronome_during_playback_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = metronome.duringPlayback,
                        enabled = metronome.enabled,
                        onCheckedChange = { onChange(metronome.copy(duringPlayback = it)) }
                    )
                }
                Text(stringResource(Res.string.metronome_tempo), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val slower = stringResource(Res.string.metronome_slower)
                    val faster = stringResource(Res.string.metronome_faster)
                    FilledTonalIconButton(
                        onClick = { setBpm(metronome.bpm - 1) },
                        modifier = Modifier.semantics { contentDescription = slower }
                    ) { Text("−") }
                    Text(
                        text = formatResource(Res.string.metronome_bpm, metronome.bpm),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = { setBpm(metronome.bpm + 1) },
                        modifier = Modifier.semantics { contentDescription = faster }
                    ) { Text("+") }
                }
                Slider(
                    value = metronome.bpm.toFloat(),
                    onValueChange = { setBpm(it.roundToInt()) },
                    valueRange = Metronome.MIN_BPM.toFloat()..Metronome.MAX_BPM.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Text(
                        stringResource(Res.string.metronome_volume),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatResource(Res.string.value_percent, (metronome.volume * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Slider(
                    value = metronome.volume,
                    onValueChange = { onChange(metronome.copy(volume = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) } }
    )
}
