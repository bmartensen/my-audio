package com.martensening.my_audio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/** Keeps the display on while [enabled] (Android); a no-op on the desktop. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

/** System back (Android back gesture); on the desktop the screens offer their own back arrow. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * Text input without suggestions, autocorrection or learning, so chords stay as typed.
 * Android sets the IME flags; desktop keyboards never autocorrect.
 */
@Composable
expect fun NoSuggestionsKeyboard(content: @Composable () -> Unit)

/** Runs [action] when the app goes to the background (Android: the activity stops); a no-op on the desktop. */
@Composable
expect fun OnAppBackground(action: () -> Unit)

/** Full-width editor dialog that handles insets and the keyboard itself where the platform has them. */
expect fun editorDialogProperties(): DialogProperties
