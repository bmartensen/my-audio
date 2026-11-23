package com.martensening.my_audio.ui

import com.martensening.my_audio.data.SoundCraftImport
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.martensening.my_audio.R
import com.martensening.my_audio.audio.AudioImport
import com.martensening.my_audio.audio.NativeAudio
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.data.ProjectArchive
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.get
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository.get(application)
    private val _projects = MutableStateFlow<List<Project>?>(null)

    /** Null until the first load finished. */
    val projects: StateFlow<List<Project>?> = _projects.asStateFlow()

    private val _importing = MutableStateFlow(false)

    /** True while a ZIP is being imported; the import icon shows progress meanwhile. */
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun refresh() = launchIo { }

    fun createProject(name: String, onCreated: (Project) -> Unit) {
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) {
                repository.create(name, NativeAudio.SAMPLE_RATE, System.currentTimeMillis())
            }
            onCreated(project)
        }
    }

    fun rename(project: Project, name: String) = launchIo { repository.rename(project.id, name) }

    fun delete(project: Project) = launchIo { repository.delete(project.id) }

    /**
     * Imports a ZIP as a new project: a backup when it holds `project.json`, a SoundCraft session when it holds a
     * `.scraft` file, otherwise one track per audio file. [onDone] receives the project, or null if the file is neither.
     */
    fun importProject(uri: Uri, onDone: (Project?) -> Unit) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val work = File(app.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
            val project = try {
                withContext(Dispatchers.IO) {
                    val zipFile = File(work, "import.zip")
                    checkNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                        zipFile.outputStream().use { input.copyTo(it) }
                    }
                    val entries = openZip(zipFile).use { zip -> zip.entries().toList().map { it.name } }
                    if (ProjectRepository.PROJECT_FILE in entries) {
                        zipFile.inputStream().use { repository.importBackup(it, System.currentTimeMillis()) }
                    } else if (SoundCraftImport.isSession(entries)) {
                        openZip(zipFile).use { zip ->
                            SoundCraftImport.import(zip, work, repository, System.currentTimeMillis(),
                                AudioImport.displayName(app, uri)?.substringBeforeLast('.') ?: app.getString(R.string.import_default_name)
                            ) { source, target -> AudioImport.decodeToWav(source, target, NativeAudio.SAMPLE_RATE) }
                        }
                    } else {
                        importAudio(zipFile, work, AudioImport.displayName(app, uri))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Project import failed", error)
                null
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    work.deleteRecursively()
                    _projects.value = repository.list()
                }
                _importing.value = false
            }
            onDone(project)
        }
    }

    /** One track per audio file of [zipFile], each converted like "Import track"; all or nothing. */
    private suspend fun importAudio(zipFile: File, work: File, zipName: String?): Project? {
        val app = getApplication<Application>()
        val takes = openZip(zipFile).use { zip ->
            val names = ProjectArchive.audioEntries(zip.entries().toList().map { it.name })
            if (names.isEmpty()) return null
            names.mapIndexed { index, name ->
                // Entry paths never become target paths; each file is unpacked under a fixed name.
                val source = File(work, "source.${name.substringAfterLast('.').lowercase()}")
                zip.getInputStream(zip.getEntry(name)).use { input -> source.outputStream().use { input.copyTo(it) } }
                val target = File(work, "$index.wav")
                val frames = try {
                    AudioImport.decodeToWav(source, target, NativeAudio.SAMPLE_RATE)
                } finally {
                    source.delete()
                }
                val trackName = ProjectArchive.trackName(name) ?: app.getString(R.string.track_default_name, index + 1)
                Log.i(TAG, "Imported $name as $trackName: $frames frames")
                ProjectRepository.ImportedTake(trackName, target, frames, com.martensening.my_audio.audio.PcmWavInfo.read(target).channels)
            }
        }
        val now = System.currentTimeMillis()
        val name = zipName ?: app.getString(R.string.import_default_name)
        return repository.createFromTakes(name, NativeAudio.SAMPLE_RATE, takes, now)
    }

    /** ZIPs from Windows tools may name entries in the old DOS code page instead of UTF-8. */
    private fun openZip(file: File): ZipFile = try {
        ZipFile(file).apply {
            try {
                entries().toList()   // fails on names that are not UTF-8
            } catch (error: Exception) {
                close()
                throw error
            }
        }
    } catch (error: Exception) {
        ZipFile(file, Charset.forName("IBM437"))
    }

    private companion object {
        const val TAG = "MyAudioImport"
    }

    private fun launchIo(change: () -> Unit) {
        viewModelScope.launch {
            _projects.value = withContext(Dispatchers.IO) {
                change()
                repository.list()
            }
        }
    }
}
