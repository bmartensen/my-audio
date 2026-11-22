package com.martensening.my_audio.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.martensening.my_audio.R
import kotlinx.coroutines.launch
import java.util.Date

/** Android host of the shared [HomeContent]: view model, microphone permission and file picker. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenProject: (id: Long, autoRecord: Boolean) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionMissing = stringResource(R.string.error_microphone_permission)

    LaunchedEffect(Unit) { viewModel.refresh() }

    fun defaultName(): String {
        val now = Date()
        return resources.getString(
            R.string.project_default_name,
            DateFormat.getMediumDateFormat(context).format(now) + " " + DateFormat.getTimeFormat(context).format(now)
        )
    }

    val startNewRecording = rememberMicrophoneAccess(
        onGranted = {
            // Never blocked by a missing calibration; the project shows the banner and warns at the take.
            viewModel.createProject(defaultName()) { onOpenProject(it.id, true) }
        },
        onDenied = { scope.launch { snackbarHostState.showSnackbar(permissionMissing) } }
    )

    // A backup ZIP from "Share → Back up project" comes back as a new project and opens; a ZIP
    // without project.json becomes a new project with one track per audio file.
    val importFailed = stringResource(R.string.import_failed)
    val importProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.importProject(uri) { project ->
            // Runs only while the start page is still shown; otherwise the list shows the project later.
            scope.launch {
                if (project != null) onOpenProject(project.id, false)
                else snackbarHostState.showSnackbar(importFailed)
            }
        }
    }

    HomeContent(
        projects = projects,
        importing = importing,
        snackbarHostState = snackbarHostState,
        onOpenLibrary = onOpenLibrary,
        onImport = { importProject.launch(ZIP_TYPES) },
        onOpenSettings = onOpenSettings,
        onRecord = startNewRecording,
        // No microphone yet: permission and calibration are asked for at the first take.
        onNewProject = { viewModel.createProject(defaultName()) { onOpenProject(it.id, false) } },
        onOpenProject = { onOpenProject(it.id, false) },
        onRename = viewModel::rename,
        onDelete = viewModel::delete
    )
}

/**
 * Content types file pickers use for ZIP archives, plus untyped files: a backup that lost its ".zip"
 * on the way is still offered; the import decides by content.
 */
private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream")
