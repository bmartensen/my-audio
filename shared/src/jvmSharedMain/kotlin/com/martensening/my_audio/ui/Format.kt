package com.martensening.my_audio.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A text with Java format arguments. Compose resources only replace %1$s/%1$d; texts such as
 * "%1$.1f Hz" are formatted here like Android's getString (default locale, so "1,5" in German).
 */
@Composable
fun formatResource(resource: StringResource, vararg args: Any): String = stringResource(resource).format(*args)
