package com.martensening.my_audio.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.martensening.my_audio.R
import com.martensening.my_audio.audio.CalibrationResult
import com.martensening.my_audio.audio.LatencyCalibration
import com.martensening.my_audio.audio.SessionMeasurement
import com.martensening.my_audio.ui.theme.CheckGreen
import com.martensening.my_audio.ui.theme.MyAudioTheme
import com.martensening.my_audio.ui.theme.RecordRed

@Composable
fun CalibrationScreen(onBack: () -> Unit, viewModel: CalibrationViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val route = rememberAudioRoute()
    LaunchedEffect(route) { viewModel.refreshRoute() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startCalibration() else viewModel.reportMissingPermission()
    }
    val onStart = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startCalibration() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Avoid opening a project while its independent calibration streams are still recording.
    BackHandler(enabled = state.runningSession != null) {}
    CalibrationContent(state = state, onBack = { if (state.runningSession == null) onBack() }, onStart = onStart)
}

/** Selected devices, the result as one number with status pills, one sentence of instruction. */
@Composable
private fun CalibrationContent(state: CalibrationUiState, onBack: () -> Unit, onStart: () -> Unit) {
    Scaffold(
        topBar = { BackTopAppBar(title = stringResource(R.string.calibration_title), onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PanelCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceRow(
                        icon = R.drawable.ic_volume_up,
                        label = R.string.audio_output,
                        name = state.route.output?.name ?: stringResource(R.string.audio_system_default)
                    )
                    DeviceRow(
                        icon = R.drawable.ic_mic,
                        label = R.string.audio_input,
                        name = state.route.input?.name ?: stringResource(R.string.audio_internal_mic)
                    )
                }
            }
            CalibrationCheck(state)
            val instruction = when {
                !state.route.ready -> R.string.audio_choose_output
                state.route.builtIn -> R.string.calibration_instruction_speaker
                else -> R.string.calibration_instruction_devices
            }
            Text(
                text = stringResource(instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = PanelMaxWidth)
            )
            Button(
                onClick = onStart,
                enabled = state.runningSession == null && state.route.ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = PanelMaxWidth)
            ) {
                Text(stringResource(if (state.saved == null) R.string.calibration_start else R.string.calibration_restart))
            }
        }
    }
}

@Composable
private fun DeviceRow(@DrawableRes icon: Int, @StringRes label: Int, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Latency as one large number; green or red frame and pills for status, stability and signal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalibrationCheck(state: CalibrationUiState) {
    val running = state.runningSession
    val result = if (running == null) state.saved ?: state.rejected else null
    val passed = running == null && state.saved != null
    val failed = running == null && state.errorStatus != null
    val accent = when {
        passed -> CheckGreen
        failed -> RecordRed
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    PanelCard(border = BorderStroke(2.dp, accent)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.calibration_latency),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = result?.let { stringResource(R.string.calibration_latency_value, it.latencyMs) } ?: "–",
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum")
            )
            if (running != null) {
                Text(
                    text = stringResource(R.string.calibration_running, running, LatencyCalibration.SESSION_COUNT),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(state, passed, failed)
                    if (result != null) ResultPills(result, stable = result.isStable(state.route.maxSpreadMs))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: CalibrationUiState, passed: Boolean, failed: Boolean) {
    val label = when {
        failed -> statusLabel(state.errorStatus!!)
        passed -> R.string.calibration_status_ok
        else -> R.string.calibration_status_none
    }
    when {
        passed -> Pill(stringResource(label), CheckGreen, R.drawable.ic_check_circle)
        failed -> Pill(stringResource(label), RecordRed, R.drawable.ic_error)
        else -> Pill(stringResource(label), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultPills(result: CalibrationResult, stable: Boolean) {
    Pill(
        stringResource(R.string.calibration_pill_stability, result.spreadMs),
        if (stable) MaterialTheme.colorScheme.onSurfaceVariant else RecordRed
    )
    Pill(stringResource(R.string.calibration_pill_signal, result.minSnrDb), MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Small rounded label; [color] tints text, icon and a light background. */
@Composable
private fun Pill(text: String, color: Color, @DrawableRes icon: Int? = null) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@StringRes
private fun statusLabel(status: Int) = when (status) {
    CalibrationViewModel.STATUS_UNSTABLE -> R.string.calibration_status_unstable
    CalibrationViewModel.STATUS_DROPOUTS -> R.string.calibration_status_dropouts
    CalibrationViewModel.STATUS_ROUTE_CHANGED -> R.string.calibration_status_route_changed
    CalibrationViewModel.STATUS_PERMISSION_DENIED -> R.string.calibration_status_permission
    SessionMeasurement.STATUS_NO_SIGNAL -> R.string.calibration_status_no_signal
    SessionMeasurement.STATUS_TIMEOUT -> R.string.calibration_status_timeout
    else -> R.string.calibration_status_audio
}

@Preview(showBackground = true)
@Composable
private fun CalibrationScreenPreview() {
    MyAudioTheme { CalibrationContent(state = CalibrationUiState(), onBack = {}, onStart = {}) }
}
