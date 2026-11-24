package com.martensening.my_audio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun OnAppBackground(action: () -> Unit) = Unit

@Composable
actual fun NoSuggestionsKeyboard(content: @Composable () -> Unit) = content()

actual fun editorDialogProperties() = DialogProperties(usePlatformDefaultWidth = false)
