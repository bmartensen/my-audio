package com.martensening.my_audio.desktop

import com.martensening.my_audio.audio.AudioSettings
import com.martensening.my_audio.ui.AudioBufferChoice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.audio.LatencyCalibration
import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import com.martensening.my_audio.ui.PanelCard
import com.martensening.my_audio.ui.formatResource
import com.martensening.my_audio.ui.theme.CheckGreen
import com.martensening.my_audio.ui.theme.RecordRed
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop settings: the audio devices (system defaults for now) and the latency calibration with
 * the same green/red check as on Android.
 */
@Composable
fun DesktopSettingsDialog(devices: DesktopDevices, calibration: DesktopCalibration, onOpenAbout: () -> Unit, onDismiss: () -> Unit) {
    val running = calibration.runningSession
    AlertDialog(
        onDismissRequest = { if (running == null) onDismiss() },
        title = { Text(stringResource(Res.string.settings_title)) },
        text = {
            // Scrolls (mouse wheel) in small windows so the info section stays reachable.
            val scroll = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(460.dp).verticalScroll(scroll)
            ) {
                Text(stringResource(Res.string.settings_section_audio), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                DeviceChooser(stringResource(Res.string.audio_output), devices.output, devices.outputs(), running == null) {
                    devices.select(output = it)
                    calibration.refresh()
                }
                DeviceChooser(stringResource(Res.string.audio_input), devices.input, devices.inputs(), running == null) {
                    devices.select(input = it)
                    calibration.refresh()
                }
                // Each buffer choice is calibrated alone, so the check below follows it.
                Text(stringResource(Res.string.audio_buffer_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
                AudioBufferChoice(AudioSettings.forRoot(DesktopStorage.root), enabled = running == null,
                    onChange = calibration::refresh)
                Text(stringResource(Res.string.calibration_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(Res.string.calibration_instruction_desktop), style = MaterialTheme.typography.bodyMedium)
                CalibrationCheck(calibration)
                Text(stringResource(Res.string.settings_section_info), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(onClick = onOpenAbout, enabled = running == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(Res.drawable.ic_info), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(Res.string.about_title), modifier = Modifier.weight(1f))
                    Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = calibration::start, enabled = running == null) {
                    Text(
                        when {
                            running != null -> formatResource(Res.string.calibration_running, running, LatencyCalibration.SESSION_COUNT)
                            calibration.saved == null -> stringResource(Res.string.calibration_start)
                            else -> stringResource(Res.string.calibration_restart)
                        }
                    )
                }
                TextButton(onClick = onDismiss, enabled = running == null) { Text(stringResource(Res.string.action_done)) }
            }
        }
    )
}

/** Single choice of a device by name; "" is the system default. A missing device is shown as such. */
@Composable
private fun DeviceChooser(label: String, selected: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val default = stringResource(Res.string.audio_system_default)
    val shown = when {
        selected.isEmpty() -> default
        selected in options -> selected
        else -> formatResource(Res.string.audio_device_unavailable, selected, default)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(shown, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (listOf("") + options).forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name.ifEmpty { default }) },
                        onClick = {
                            expanded = false
                            onSelect(name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalibrationCheck(calibration: DesktopCalibration) {
    val saved = calibration.saved
    val failure = calibration.failure
    val color = when {
        saved != null -> CheckGreen
        failure != null -> RecordRed
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    PanelCard(border = BorderStroke(2.dp, color)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    when (failure) {
                        DesktopCalibration.Failure.Unstable -> Res.string.calibration_status_unstable
                        DesktopCalibration.Failure.NoSignal -> Res.string.calibration_status_no_signal
                        DesktopCalibration.Failure.Audio -> Res.string.calibration_status_audio
                        DesktopCalibration.Failure.RouteChanged -> Res.string.calibration_status_route_changed
                        null -> if (saved != null) Res.string.calibration_status_ok else Res.string.calibration_status_none
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (saved != null || failure != null) color else MaterialTheme.colorScheme.onSurface
            )
            if (saved != null) {
                DeviceLine(stringResource(Res.string.calibration_latency), formatResource(Res.string.calibration_latency_value, saved.latencyMs))
                Text(formatResource(Res.string.calibration_pill_stability, saved.spreadMs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            calibration.rejectedSpreadMs?.let {
                Text(formatResource(Res.string.calibration_pill_stability, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
