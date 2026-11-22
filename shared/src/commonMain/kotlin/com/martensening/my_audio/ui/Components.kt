package com.martensening.my_audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import com.martensening.my_audio.ui.theme.RecordRed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = stringResource(Res.string.action_back)
                )
            }
        },
        actions = { actions() }
    )
}

/** Corner radius of all content panels: track cards, envelope editor and calibration check. */
val PanelShape = RoundedCornerShape(18.dp)

/** Widest panel; keeps waveforms and lines readable in landscape and on wide screens. */
val PanelMaxWidth = 720.dp

/** Width of the transport column at the right edge in landscape (project and envelope editor). */
val TransportRailWidth = 168.dp

/** Rounded, outlined panel in the style of the track cards. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = PanelMaxWidth),
        content = content
    )
}

/** Large round record control; shows a stop square while [recording]. */
@Composable
fun RecordButton(recording: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val description = stringResource(if (recording) Res.string.action_stop_recording else Res.string.action_record)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(4.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(84.dp)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (recording) {
                Surface(shape = RoundedCornerShape(6.dp), color = RecordRed, modifier = Modifier.size(32.dp)) {}
            } else {
                Surface(
                    shape = CircleShape,
                    color = if (enabled) RecordRed else RecordRed.copy(alpha = 0.38f),
                    modifier = Modifier.size(60.dp)
                ) {}
            }
        }
    }
}

/** Shared delete confirmation ("… löschen?" / Löschen / Abbrechen). */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: StringResource = Res.string.action_delete
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(confirmLabel), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
fun RenameDialog(title: String, initialName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty(), onClick = { onSave(trimmed); onDismiss() }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

/** Formats project frames as m:ss.t; negative during the count-in. */
fun formatTime(frames: Long, sampleRate: Int): String {
    val tenths = abs(frames) * 10 / sampleRate
    val sign = if (frames < 0) "−" else ""
    return "$sign${tenths / 600}:${(tenths / 10 % 60).toString().padStart(2, '0')}.${tenths % 10}"
}

/** Formats project frames as m:ss. */
fun formatDuration(frames: Long, sampleRate: Int): String {
    val seconds = frames.coerceAtLeast(0) / sampleRate
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
