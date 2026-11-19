package com.martensening.my_audio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.martensening.my_audio.data.EffectLibrary

/** Android host of the shared [LibraryContent]; the selection lives next to the projects. */
@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val library = remember { EffectLibrary.forRoot(context.applicationContext.filesDir) }
    LibraryContent(library = library, onBack = onBack)
}
