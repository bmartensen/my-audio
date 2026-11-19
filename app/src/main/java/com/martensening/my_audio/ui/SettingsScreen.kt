package com.martensening.my_audio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.R
import com.martensening.my_audio.audio.CalibrationStore
import com.martensening.my_audio.ui.theme.MyAudioTheme

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCalibration: () -> Unit) {
    val route = rememberAudioRoute()
    val calibration = CalibrationStore(LocalContext.current).load(route)
    val summary = if (!route.ready) stringResource(R.string.audio_choose_output)
        else if (calibration == null) stringResource(R.string.audio_not_calibrated)
        else stringResource(R.string.calibration_latency_value, calibration.latencyMs)
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    SettingsContent(onBack, onOpenCalibration, summary, version, onOpenAbout = { aboutOpen = true }) { AudioDevicePreferences() }
    if (aboutOpen) AboutDialog(version = version, onDismiss = { aboutOpen = false })
}

@Composable
private fun SettingsContent(onBack: () -> Unit, onOpenCalibration: () -> Unit,
                            calibrationSummary: String, version: String, onOpenAbout: () -> Unit,
                            audioDevices: @Composable () -> Unit) {
    Scaffold(
        topBar = { BackTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel(stringResource(R.string.settings_section_audio))
            audioDevices()
            ListItem(
                headlineContent = { Text(stringResource(R.string.calibration_title)) },
                supportingContent = { Text(calibrationSummary) },
                leadingContent = { Icon(painterResource(R.drawable.ic_graphic_eq), contentDescription = null) },
                trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenCalibration)
            )
            SectionLabel(stringResource(R.string.settings_section_info))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_title)) },
                supportingContent = { Text(stringResource(R.string.about_summary, version)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAbout)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MyAudioTheme {
        SettingsContent({}, {}, "21.2 ms", "0.6", {}) {
            ListItem(headlineContent = { Text(stringResource(R.string.audio_output)) },
                supportingContent = { Text(stringResource(R.string.audio_system_default)) })
            ListItem(headlineContent = { Text(stringResource(R.string.audio_input)) },
                supportingContent = { Text(stringResource(R.string.audio_internal_mic)) })
        }
    }
}
