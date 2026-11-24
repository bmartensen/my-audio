package com.martensening.my_audio.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        if (enabled) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun OnAppBackground(action: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(action)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) current() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/**
 * "Autocorrect off" alone is only a hint to the keyboard; these platform flags make Gboard stop
 * suggesting and learning (it replaced chords like "Fm" with "Gm").
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun NoSuggestionsKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val withoutSuggestions = object : PlatformTextInputMethodRequest by request {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    outAttributes.inputType = outAttributes.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    return connection
                }
            }
            nextHandler.startInputMethod(withoutSuggestions)
        },
        content = content
    )
}

actual fun editorDialogProperties() = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
