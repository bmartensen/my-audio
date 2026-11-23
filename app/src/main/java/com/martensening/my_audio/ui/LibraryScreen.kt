package com.martensening.my_audio.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.martensening.my_audio.data.EffectLibrary
import com.martensening.my_audio.data.ToneImportResult
import com.martensening.my_audio.data.ToneLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android host of the shared [LibraryContent]; the selection and the tone library live next to the projects.
 * Tones are picked with the system file picker: `.nam`, `.wav` or the ZIP as TONE3000 delivers it.
 */
@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val root = context.applicationContext.filesDir
    val library = remember { EffectLibrary.forRoot(root) }
    val tones = remember { ToneLibrary.forRoot(root) }
    val scope = rememberCoroutineScope()
    var onResult by remember { mutableStateOf<((ToneImportResult?) -> Unit)?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val report = onResult ?: return@rememberLauncherForActivityResult
        onResult = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                uris.map { uri -> importTone(context, tones, uri) }.reduceOrNull { a, b ->
                    if (a == null) b else if (b == null) a
                    else ToneImportResult(a.added + b.added, a.duplicates + b.duplicates, a.failed + b.failed)
                }
            }
            report(result)
        }
    }
    LibraryContent(
        library = library,
        onBack = onBack,
        tones = tones,
        onImportTones = { report ->
            onResult = report
            // .nam has no registered MIME type, so every file is offered; the import checks the content.
            pick.launch(arrayOf("*/*"))
        }
    )
}

private fun importTone(context: android.content.Context, tones: ToneLibrary, uri: Uri): ToneImportResult? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()
    context.contentResolver.openInputStream(uri)?.use { tones.import(name, it) }
}.getOrNull()
