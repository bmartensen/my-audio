package com.martensening.my_audio.desktop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.desktop_saved
import com.martensening.my_audio.resources.import_failed
import com.martensening.my_audio.resources.project_default_name
import com.martensening.my_audio.ui.AboutDialog
import com.martensening.my_audio.ui.HomeContent
import com.martensening.my_audio.ui.LibraryContent
import com.martensening.my_audio.ui.ProjectContent
import com.martensening.my_audio.ui.ProjectSessions
import com.martensening.my_audio.ui.ShareRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Which screen the window shows; projects open like on Android, without a back stack beyond that. */
private sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data class Project(val id: Long, val autoRecord: Boolean) : Screen
}

/**
 * Desktop host of the shared screens: start page, project view, the settings with calibration and
 * the about dialog ([aboutOpen], also opened with F1 by the window).
 */
@Composable
fun DesktopApp(window: Frame, version: String, aboutOpen: MutableState<Boolean>) {
    val scope = rememberCoroutineScope()
    val repository = remember { DesktopStorage.repository() }
    val devices = remember { DesktopDevices(DesktopStorage.root) }
    val calibrationStore = remember { DesktopCalibrationStore(DesktopStorage.root) }
    val platform = remember { DesktopProjectPlatform(repository, devices, calibrationStore, DesktopStorage.root) }
    val calibration = remember { DesktopCalibration(calibrationStore, devices, scope) }
    val home = remember { DesktopHome(repository, platform.cacheDir, scope) }
    // Development runs can start in a project: gradlew :desktop:run -PmyAudio.openProject=<id> (screenshots).
    var screen by remember {
        mutableStateOf<Screen>(
            System.getProperty("myaudio.openProject")?.toLongOrNull()?.let { Screen.Project(it, false) } ?: Screen.Home
        )
    }
    var settingsOpen by remember { mutableStateOf(false) }

    when (val current = screen) {
        Screen.Home -> DesktopHomeScreen(
            window = window,
            home = home,
            onOpenProject = { id, autoRecord -> screen = Screen.Project(id, autoRecord) },
            onRecord = {
                // A new device pair is calibrated before its first take, as on Android.
                if (calibrationStore.latencyFrames(devices.route()) == null) settingsOpen = true
                else scope.launch {
                    home.create(getString(Res.string.project_default_name, now())) { screen = Screen.Project(it.id, true) }
                }
            },
            onNewProject = {
                scope.launch {
                    home.create(getString(Res.string.project_default_name, now())) { screen = Screen.Project(it.id, false) }
                }
            },
            onOpenSettings = { settingsOpen = true },
            onOpenLibrary = { screen = Screen.Library }
        )
        Screen.Library -> LibraryContent(library = platform.effectLibrary, onBack = { screen = Screen.Home })
        is Screen.Project -> DesktopProjectScreen(
            window = window,
            platform = platform,
            projectId = current.id,
            autoRecord = current.autoRecord,
            onAutoRecordHandled = { screen = current.copy(autoRecord = false) },
            onBack = {
                screen = Screen.Home
                home.refresh()
            },
            // The settings open over the project, so "Calibrate now" returns to it afterwards.
            settingsOpen = settingsOpen,
            onOpenSettings = { settingsOpen = true }
        )
    }
    if (settingsOpen) DesktopSettingsDialog(
        devices, calibration,
        onOpenAbout = {
            settingsOpen = false
            aboutOpen.value = true
        },
        onDismiss = { settingsOpen = false }
    )
    if (aboutOpen.value) AboutDialog(version = version, onDismiss = { aboutOpen.value = false })
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DesktopHomeScreen(
    window: Frame,
    home: DesktopHome,
    onOpenProject: (Long, Boolean) -> Unit,
    onRecord: () -> Unit,
    onNewProject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { home.refresh() }

    fun import(file: File) = home.importZip(file) { project ->
        if (project != null) onOpenProject(project.id, false)
        else scope.launch { snackbarHostState.showSnackbar(getString(Res.string.import_failed)) }
    }

    // Files dragged from Explorer onto the window are imported like with the import button. The
    // content decides, not the name: shared backups sometimes lose their ".zip" on the way.
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val file = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
                    .map { File(URI(it)) }
                    .firstOrNull { it.isFile } ?: return false
                import(file)
                return true
            }
        }
    }

    HomeContent(
        projects = home.projects,
        importing = home.importing,
        snackbarHostState = snackbarHostState,
        onOpenLibrary = onOpenLibrary,
        onImport = { chooseFile(window, ZIP_PATTERN) { name -> name.endsWith(".zip", true) || '.' !in name }?.let(::import) },
        onOpenSettings = onOpenSettings,
        onRecord = onRecord,
        onNewProject = onNewProject,
        onOpenProject = { onOpenProject(it.id, false) },
        onRename = home::rename,
        onDelete = home::delete,
        modifier = Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { it.dragData() is DragData.FilesList },
            target = dropTarget
        )
    )
}

@Composable
private fun DesktopProjectScreen(
    window: Frame,
    platform: DesktopProjectPlatform,
    projectId: Long,
    autoRecord: Boolean,
    onAutoRecordHandled: () -> Unit,
    onBack: () -> Unit,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit
) {
    val session = remember(projectId) { ProjectSessions.get(platform, projectId) }
    val snackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(session) { onDispose { session.closeTuner() } }
    // Closing the settings counts as returning to the project: pick up a new calibration or device pair.
    var settingsWereOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) settingsWereOpen = true
        else if (settingsWereOpen) {
            settingsWereOpen = false
            session.onEnter()
        }
    }
    LaunchedEffect(session) {
        session.shareRequests.collect { request ->
            val saved = saveAs(window, request) ?: return@collect
            snackbarHostState.showSnackbar(getString(Res.string.desktop_saved, saved.absolutePath))
        }
    }
    Box(Modifier.fillMaxSize()) {
        ProjectContent(
            session = session,
            projectId = projectId,
            autoRecord = autoRecord,
            onAutoRecordHandled = onAutoRecordHandled,
            onBack = onBack,
            onOpenCalibration = onOpenSettings,
            snackbarHostState = snackbarHostState,
            onRequestRecording = session::toggleRecording,
            onRequestTuner = session::openTuner,
            onImportTrack = {
                chooseFile(window, AUDIO_PATTERN) { name -> name.substringAfterLast('.', "").lowercase() in DesktopAudioImport.EXTENSIONS }
                    ?.let { session.importTrack(FileImportSource(it)) }
            }
        )
    }
}

/** "Share" on the desktop: the export is saved where the user chooses. */
private suspend fun saveAs(window: Frame, request: ShareRequest): File? {
    val dialog = FileDialog(window, null as String?, FileDialog.SAVE).apply {
        file = request.file.name
        isVisible = true
    }
    val chosen = dialog.file ?: return null
    // A name typed without extension still gets it, so the file opens with the right program.
    val extension = request.file.extension
    val name = if (extension.isEmpty() || chosen.endsWith(".$extension", ignoreCase = true)) chosen else "$chosen.$extension"
    val target = File(dialog.directory, name)
    withContext(Dispatchers.IO) { request.file.copyTo(target, overwrite = true) }
    return target
}

/** The native file dialog; [pattern] is the Windows filter, [accept] the filter elsewhere. */
private fun chooseFile(window: Frame, pattern: String, accept: (String) -> Boolean): File? {
    val dialog = FileDialog(window, null as String?, FileDialog.LOAD).apply {
        file = pattern
        setFilenameFilter { _, name -> accept(name) }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

private const val ZIP_PATTERN = "*.zip;*."
private const val AUDIO_PATTERN = "*.mp3;*.wav;*.flac;*.ogg;*.oga;*.opus"

/** Date and time for the default name of a new project. */
private fun now(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
