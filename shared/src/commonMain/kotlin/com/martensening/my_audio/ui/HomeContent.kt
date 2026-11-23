package com.martensening.my_audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The start page, shared by Android and desktop: project cards with rename/delete and a "New
 * project" action below them, import and settings at the top, the big Record button (quick start:
 * new project and take at once) at the bottom, or in a right-side rail in landscape.
 * The platform supplies data and actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    /** Null until the first load finished. */
    projects: List<Project>?,
    importing: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenLibrary: () -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecord: () -> Unit,
    /** Creates an empty project and opens it without recording. */
    onNewProject: () -> Unit,
    onOpenProject: (Project) -> Unit,
    onRename: (Project, String) -> Unit,
    onDelete: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val landscape = LocalWindowInfo.current.containerSize.let { it.width > it.height }
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_graphic_eq),
                            contentDescription = stringResource(Res.string.library_title)
                        )
                    }
                    IconButton(onClick = onImport, enabled = !importing) {
                        if (importing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(
                            painter = painterResource(Res.drawable.ic_import),
                            contentDescription = stringResource(Res.string.import_backup)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_settings),
                            contentDescription = stringResource(Res.string.settings_title)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!landscape) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp, top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                RecordButton(recording = false, onClick = onRecord)
            }
        }
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val list = projects
                when {
                    list == null -> Unit
                    list.isEmpty() -> EmptyState(onNewProject, Modifier)
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(list, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onRename = { renaming = project.id },
                                onDelete = { deleting = project.id }
                            )
                        }
                        item(key = "new-project") {
                            AddItemButton(stringResource(Res.string.home_new_project), onNewProject)
                        }
                    }
                }
            }
            if (landscape) {
                TransportPanel(vertical = true) {
                    LandscapeTransportRail(
                        topBarHeight = (innerPadding.calculateTopPadding() - safeTop).coerceAtLeast(0.dp)
                    ) {
                        RecordButton(recording = false, onClick = onRecord)
                    }
                }
            }
        }
    }

    projects?.firstOrNull { it.id == renaming }?.let { project ->
        RenameDialog(
            title = stringResource(Res.string.project_rename_title),
            initialName = project.name,
            onSave = { onRename(project, it) },
            onDismiss = { renaming = null }
        )
    }
    projects?.firstOrNull { it.id == deleting }?.let { project ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.project_delete_question),
            message = stringResource(Res.string.project_delete_info, project.name),
            onConfirm = { onDelete(project) },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun EmptyState(onNewProject: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.home_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Box(Modifier.padding(top = 24.dp)) {
            AddItemButton(stringResource(Res.string.home_new_project), onNewProject)
        }
    }
}

/** Project card used by the shared home screen. */
@Composable
private fun ProjectCard(project: Project, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.project_summary,
                        project.tracks.size,
                        project.tracks.size,
                        formatDuration(project.lengthFrames, project.sampleRate)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    painterResource(Res.drawable.ic_edit),
                    contentDescription = stringResource(Res.string.project_rename_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.project_delete_question),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
