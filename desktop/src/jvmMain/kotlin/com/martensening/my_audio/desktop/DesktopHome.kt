package com.martensening.my_audio.desktop

import com.martensening.my_audio.data.SoundCraftImport
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.martensening.my_audio.audio.NativeAudio
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.data.ProjectArchive
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.platform.Log
import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.track_default_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.zip.ZipFile

/** State of the desktop start page; storage work runs off the UI thread like on Android. */
class DesktopHome(
    private val repository: ProjectRepository,
    private val cacheDir: File,
    private val scope: CoroutineScope
) {
    /** Null until the first load finished. */
    var projects by mutableStateOf<List<Project>?>(null)
        private set
    var importing by mutableStateOf(false)
        private set

    fun refresh() = io { }

    fun rename(project: Project, name: String) = io { repository.rename(project.id, name) }

    fun delete(project: Project) = io { repository.delete(project.id) }

    fun create(name: String, onCreated: (Project) -> Unit) {
        scope.launch {
            val project = withContext(Dispatchers.IO) {
                repository.create(name, NativeAudio.SAMPLE_RATE, System.currentTimeMillis())
            }
            onCreated(project)
        }
    }

    /**
     * Imports a ZIP as a new project, decided by its content like on Android: a backup when it
     * holds `project.json`, a SoundCraft session when it holds a `.scraft` file, otherwise one track per audio file. [onDone] receives null on failure.
     */
    fun importZip(file: File, onDone: (Project?) -> Unit) = importFiles(listOf(file)) { created, failed ->
        onDone(created.singleOrNull()?.takeIf { failed == 0 })
    }

    /**
     * Imports files dropped onto the window: each ZIP becomes a project (see [importZip]), and loose audio files
     * together become one project with a track each, like a ZIP of them. [onDone] receives the created projects
     * and the number of files that could not be imported.
     */
    fun importFiles(files: List<File>, onDone: (created: List<Project>, failed: Int) -> Unit) {
        if (importing || files.isEmpty()) return
        importing = true
        scope.launch {
            val work = File(cacheDir, "import").apply { deleteRecursively(); mkdirs() }
            val created = mutableListOf<Project>()
            var failed = 0
            withContext(Dispatchers.IO) {
                val (audio, archives) = files.partition { it.extension.lowercase() in DesktopAudioImport.EXTENSIONS }
                for (file in archives) {
                    val project = runCatching { importArchive(file, work) }
                        .onFailure { Log.e(TAG, "Project import failed", it) }.getOrNull()
                    if (project != null) created += project else failed++
                    work.listFiles()?.forEach { it.deleteRecursively() }
                }
                if (audio.isNotEmpty()) {
                    val project = runCatching { importAudioFiles(audio, work) }
                        .onFailure { Log.e(TAG, "Audio import failed", it) }.getOrNull()
                    if (project != null) created += project else failed += audio.size
                }
                work.deleteRecursively()
            }
            projects = withContext(Dispatchers.IO) { repository.list() }
            importing = false
            onDone(created, failed)
        }
    }

    private suspend fun importArchive(file: File, work: File): Project? {
        val names = ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }
        return if (ProjectRepository.PROJECT_FILE in names) {
            file.inputStream().use { repository.importBackup(it, System.currentTimeMillis()) }
        } else if (SoundCraftImport.isSession(names)) {
            ZipFile(file).use { zip ->
                SoundCraftImport.import(zip, work, repository, System.currentTimeMillis(), file.nameWithoutExtension) { source, target ->
                    DesktopAudioImport.decodeToWav(source, target, NativeAudio.SAMPLE_RATE)
                }
            }
        } else {
            importAudio(file, work)
        }
    }

    /** Loose audio files as one project, in the order and with the names a ZIP of them would get; all or nothing. */
    private suspend fun importAudioFiles(files: List<File>, work: File): Project? {
        val byName = files.associateBy { it.name }
        val ordered = ProjectArchive.audioEntries(byName.keys.toList()).mapNotNull(byName::get)
        if (ordered.isEmpty()) return null
        val takes = ordered.mapIndexed { index, file ->
            val target = File(work, "$index.wav")
            val frames = DesktopAudioImport.decodeToWav(file, target, NativeAudio.SAMPLE_RATE)
            val trackName = ProjectArchive.trackName(file.name) ?: getString(Res.string.track_default_name, index + 1)
            ProjectRepository.ImportedTake(trackName, target, frames, com.martensening.my_audio.audio.PcmWavInfo.read(target).channels)
        }
        // Named after the folder the files came from, like a ZIP after its file.
        val name = ordered.first().absoluteFile.parentFile?.name?.takeIf { it.isNotBlank() } ?: ordered.first().nameWithoutExtension
        return repository.createFromTakes(name, NativeAudio.SAMPLE_RATE, takes, System.currentTimeMillis())
    }

    /** One track per audio file, all or nothing; see ProjectArchive.audioEntries. */
    private suspend fun importAudio(zipFile: File, work: File): Project? = ZipFile(zipFile).use { zip ->
        val names = ProjectArchive.audioEntries(zip.entries().toList().map { it.name })
        if (names.isEmpty()) return null
        val takes = names.mapIndexed { index, name ->
            // Entry paths never become target paths; each file is unpacked under a fixed name.
            val source = File(work, "source.${name.substringAfterLast('.').lowercase()}")
            zip.getInputStream(zip.getEntry(name)).use { input -> source.outputStream().use { input.copyTo(it) } }
            val target = File(work, "$index.wav")
            val frames = try {
                DesktopAudioImport.decodeToWav(source, target, NativeAudio.SAMPLE_RATE)
            } finally {
                source.delete()
            }
            val trackName = ProjectArchive.trackName(name) ?: getString(Res.string.track_default_name, index + 1)
            Log.i(TAG, "Imported $name as $trackName: $frames frames")
            ProjectRepository.ImportedTake(trackName, target, frames, com.martensening.my_audio.audio.PcmWavInfo.read(target).channels)
        }
        repository.createFromTakes(zipFile.nameWithoutExtension, NativeAudio.SAMPLE_RATE, takes, System.currentTimeMillis())
    }

    private fun io(change: () -> Unit) {
        scope.launch {
            projects = withContext(Dispatchers.IO) {
                change()
                repository.list()
            }
        }
    }

    private companion object {
        const val TAG = "MyAudioImport"
    }
}
