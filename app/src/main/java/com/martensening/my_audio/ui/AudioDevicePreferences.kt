package com.martensening.my_audio.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.martensening.my_audio.R
import com.martensening.my_audio.audio.AudioDeviceRoute
import com.martensening.my_audio.audio.AudioDevices
import com.martensening.my_audio.audio.AudioEndpoint
import kotlinx.coroutines.delay

/** Current device pair; follows connections and system media-route changes without hotplug. */
@Composable
fun rememberAudioRoute(): AudioDeviceRoute {
    val context = LocalContext.current
    val devices = remember(context) { AudioDevices(context) }
    var route by remember { mutableStateOf(devices.route()) }
    DisposableEffect(devices) {
        val unregister = devices.observe { route = devices.route() }
        onDispose { unregister() }
    }
    LaunchedEffect(devices) {
        while (true) {
            route = devices.route()
            delay(1000)
        }
    }
    return route
}

/** "Playback device" and "Microphone" entries of the settings, each with a single-choice dialog. */
@Composable
fun AudioDevicePreferences() {
    val context = LocalContext.current
    val devices = remember(context) { AudioDevices(context) }
    val route = rememberAudioRoute()
    var revision by remember { mutableIntStateOf(0) }
    var choosingInput by rememberSaveable { mutableStateOf<Boolean?>(null) }
    DisposableEffect(devices) {
        val unregister = devices.observe { revision++ }
        onDispose { unregister() }
    }

    for (input in listOf(false, true)) {
        val selected = remember(input, revision, route) { devices.selection(input) }
        val current = remember(input, revision, route) { devices.devices(input).firstOrNull { it.key == selected } }
        val fallback = stringResource(if (input) R.string.audio_internal_mic else R.string.audio_system_default)
        val summary = when {
            selected == null && !input && route.output != null ->
                stringResource(R.string.audio_default_device, fallback, route.output.name)
            selected == null -> fallback
            current != null -> current.name
            else -> stringResource(R.string.audio_device_unavailable, devices.selectedName(input).orEmpty(), fallback)
        }
        ListItem(
            headlineContent = { Text(stringResource(if (input) R.string.audio_input else R.string.audio_output)) },
            supportingContent = { Text(summary) },
            leadingContent = {
                Icon(painterResource(if (input) R.drawable.ic_mic else R.drawable.ic_volume_up), contentDescription = null)
            },
            modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = { choosingInput = input })
        )
    }

    choosingInput?.let { input ->
        DeviceChoiceDialog(
            input = input,
            selected = devices.selection(input),
            // The built-in microphone is the "default" choice and is not listed twice.
            options = devices.devices(input).filterNot { input && it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC },
            onSelect = { endpoint ->
                devices.select(input, endpoint)
                revision++
                choosingInput = null
            },
            onDismiss = { choosingInput = null }
        )
    }
}

@Composable
private fun DeviceChoiceDialog(
    input: Boolean,
    selected: String?,
    options: List<AudioEndpoint>,
    onSelect: (AudioEndpoint?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (input) R.string.audio_input else R.string.audio_output)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                (listOf(null) + options).forEach { endpoint ->
                    val checked = selected == endpoint?.key
                    ListItem(
                        headlineContent = {
                            Text(endpoint?.name ?: stringResource(
                                if (input) R.string.audio_internal_mic else R.string.audio_system_default
                            ))
                        },
                        supportingContent = if (endpoint?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            { Text(stringResource(R.string.audio_bluetooth_mic_summary)) }
                        } else null,
                        leadingContent = { RadioButton(selected = checked, onClick = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(checked, role = Role.RadioButton) { onSelect(endpoint) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
