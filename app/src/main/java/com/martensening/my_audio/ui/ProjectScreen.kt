package com.martensening.my_audio.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.martensening.my_audio.R
import com.martensening.my_audio.audio.AudioDevices
import com.martensening.my_audio.audio.AudioImport
import com.martensening.my_audio.audio.InputChannel
import kotlinx.coroutines.launch

/** Android host of the shared [ProjectContent]: session, permission, document picker and share sheet. */
@Composable
fun ProjectScreen(
    projectId: Long,
    autoRecord: Boolean,
    onAutoRecordHandled: () -> Unit,
    onBack: () -> Unit,
    onOpenCalibration: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val session = remember(projectId) { ProjectSessions.get(AndroidProjectPlatform(application), projectId) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionMissing = stringResource(R.string.error_microphone_permission)

    // The microphone is released when the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.closeTuner()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.closeTuner() }
    }
    val audioRoute = rememberAudioRoute()
    LaunchedEffect(audioRoute) { session.refreshAudioDevices() }
    // The route is polled; the switch shows a new choice at once, the next take reads it from the preferences.
    val devices = remember(context) { AudioDevices(context) }
    var chosenChannel by remember(audioRoute.input?.key) { mutableStateOf<InputChannel?>(null) }
    val inputChannel = audioRoute.inputChannel?.let { chosenChannel ?: it }

    val chooserTitle = stringResource(R.string.share_chooser_title)
    LaunchedEffect(session) {
        session.shareRequests.collect { request ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", request.file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = request.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                // Targets such as Google Drive name the saved file after the subject/title; with the
                // extension the backup arrives as "… - Projekt.zip" instead of a file without type.
                putExtra(Intent.EXTRA_SUBJECT, request.file.name)
                putExtra(Intent.EXTRA_TITLE, request.file.name)
                clipData = ClipData.newUri(context.contentResolver, request.file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }
    }

    val denied = { scope.launch { snackbarHostState.showSnackbar(permissionMissing) }; Unit }
    val requestRecording = rememberMicrophoneAccess(onGranted = session::toggleRecording, onDenied = denied)
    val requestTuner = rememberMicrophoneAccess(onGranted = session::openTuner, onDenied = denied)
    val importTrack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) session.importTrack(UriImportSource(context, uri))
    }

    ProjectContent(
        session = session,
        projectId = projectId,
        autoRecord = autoRecord,
        onAutoRecordHandled = onAutoRecordHandled,
        onBack = onBack,
        onOpenCalibration = onOpenCalibration,
        snackbarHostState = snackbarHostState,
        onRequestRecording = requestRecording,
        onRequestTuner = requestTuner,
        onImportTrack = { importTrack.launch(AudioImport.MIME_TYPES) },
        inputChannel = inputChannel,
        onInputChannel = { channel ->
            audioRoute.input?.let { devices.selectInputChannel(it, channel) }
            chosenChannel = channel
        }
    )
}
