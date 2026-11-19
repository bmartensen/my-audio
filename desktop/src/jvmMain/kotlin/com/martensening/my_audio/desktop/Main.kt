package com.martensening.my_audio.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.martensening.my_audio.ui.ProjectSessions
import java.awt.Dimension

/** Desktop twin of the Android app: the same shared screens in a larger window. */
fun main() = application {
    val version = System.getProperty("myaudio.version") ?: "dev"
    // First start: 16:10 and centred; afterwards the last position and size (DesktopWindowState).
    val windowState = remember { DesktopWindowState.initial() }
    // The about dialog opens from the settings or with F1, the usual help key on Windows.
    val aboutOpen = remember { mutableStateOf(false) }
    Window(
        onCloseRequest = {
            // A running take is finalised and registered before the app quits.
            ProjectSessions.active?.stopNow()
            DesktopWindowState.save(windowState)
            exitApplication()
        },
        // The version is shown in the about dialog, not in the title bar.
        title = "My Audio",
        icon = painterResource("icon.png"),
        state = windowState,
        onKeyEvent = { event ->
            (event.key == Key.F1 && event.type == KeyEventType.KeyDown).also { if (it) aboutOpen.value = true }
        }
    ) {
        window.minimumSize = Dimension(480, 640)
        // The fallback scheme of the Android app; Material You colours exist only on Android 12+.
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            DesktopApp(window, version, aboutOpen)
        }
    }
}
