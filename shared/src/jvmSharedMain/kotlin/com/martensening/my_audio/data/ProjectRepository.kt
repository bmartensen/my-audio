package com.martensening.my_audio.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Stores every project in its own ID directory:
 *
 * ```
 * <root>/projects/<project-id>/project.json
 * <root>/projects/<project-id>/tracks/<track-id>.wav
 * ```
 *
 * Deleting a project removes its directory including all recordings.
 */
class ProjectRepository(root: File) {
    private val projectsDir = File(root, "projects")

    @Synchronized
    fun list(): List<Project> =
        projectsDir.listFiles { file -> file.isDirectory }
            ?.mapNotNull { load(it.name.toLongOrNull() ?: return@mapNotNull null) }
            ?.sortedByDescending { it.createdAtMillis }
            .orEmpty()

    @Synchronized
    fun load(id: Long): Project? {
        val file = File(projectDir(id), PROJECT_FILE)
        if (!file.isFile) return null
        return runCatching { ProjectJson.decode(file.readText()) }.getOrNull()
    }

    @Synchronized
    fun create(name: String, sampleRate: Int, nowMillis: Long): Project {
        var id = nowMillis
        while (projectDir(id).exists()) id++
        File(projectDir(id), TRACKS_DIR).mkdirs()
        return Project(id = id, name = name, createdAtMillis = nowMillis, sampleRate = sampleRate).also(::save)
    }

    @Synchronized
    fun rename(id: Long, name: String): Project? = update(id) { it.copy(name = name) }

    @Synchronized
    fun delete(id: Long) {
        projectDir(id).deleteRecursively()
    }

    /** Reserves a file for the next take; it becomes a track only via [addTrack]. */
    @Synchronized
    fun newTrackFile(projectId: Long, nowMillis: Long): File {
        val dir = File(projectDir(projectId), TRACKS_DIR).apply { mkdirs() }
        var id = nowMillis
        while (File(dir, "$id.wav").exists()) id++
        return File(dir, "$id.wav")
    }

    @Synchronized
    fun addTrack(projectId: Long, track: Track): Project? = update(projectId) {
        it.copy(tracks = it.tracks + track, nextTrackNumber = it.nextTrackNumber + 1)
    }

    @Synchronized
    fun setMetronome(projectId: Long, metronome: Metronome): Project? = update(projectId) { it.copy(metronome = metronome) }

    @Synchronized
    fun updateTrack(projectId: Long, track: Track): Project? = update(projectId) { project ->
        project.copy(tracks = project.tracks.map { if (it.id == track.id) track else it })
    }

    @Synchronized
    fun setMasterChain(projectId: Long, chain: List<EffectSlot>): Project? =
        update(projectId) { it.copy(masterChain = chain.take(EffectSlot.MAX_PER_CHAIN)) }

    @Synchronized
    fun setLoop(projectId: Long, loop: PlaybackLoop?): Project? = update(projectId) { it.copy(loop = loop) }

    @Synchronized
    fun setNotes(projectId: Long, notes: String): Project? = update(projectId) { it.copy(notes = notes) }

    @Synchronized
    fun setNotesTextSize(projectId: Long, size: Int): Project? = update(projectId) { it.copy(notesTextSize = size) }

    @Synchronized
    fun setNotesScroll(projectId: Long, scroll: NotesScroll): Project? = update(projectId) { it.copy(notesScroll = scroll) }

    @Synchronized
    fun setPreRoll(projectId: Long, preRoll: PreRoll): Project? = update(projectId) { it.copy(preRoll = preRoll) }

    @Synchronized
    fun deleteTrack(projectId: Long, trackId: Long): Project? {
        val project = load(projectId) ?: return null
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return project
        deleteTrackFiles(projectId, track)
        return project.copy(tracks = project.tracks - track).also(::save)
    }

    /** Take, reference and derived caches all share the take's id as name prefix. */
    private fun deleteTrackFiles(projectId: Long, track: Track) {
        val prefix = File(track.fileName).nameWithoutExtension + "."
        tracksDir(projectId).listFiles { file -> file.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /**
     * Writes the project as a ZIP backup: `project.json` with the takes and their references.
     * Derived caches are left out and rebuilt after an import.
     */
    @Synchronized
    fun exportBackup(projectId: Long, target: File): Boolean {
        val project = load(projectId) ?: return false
        val takes = project.tracks.flatMap { listOfNotNull(it.fileName, it.referenceFileName) }
            .map { "$TRACKS_DIR/$it" to File(tracksDir(projectId), it) }
            .filter { it.second.isFile }
        ProjectArchive.writeZip(target, listOf(PROJECT_FILE to File(projectDir(projectId), PROJECT_FILE)) + takes)
        return true
    }

    /**
     * Imports a backup as a new project (new id, everything else kept). Only `project.json` and
     * WAV files directly below `tracks/` are unpacked, never anything else; a backup from a newer
     * format or with missing takes is rejected. Returns null for anything that is not a valid backup.
     */
    @Synchronized
    fun importBackup(input: InputStream, nowMillis: Long): Project? {
        var id = nowMillis
        while (projectDir(id).exists()) id++
        val dir = projectDir(id)
        val tracks = File(dir, TRACKS_DIR).apply { mkdirs() }
        val project = runCatching {
            var json: String? = null
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == PROJECT_FILE -> json = zip.readBytes().decodeToString()
                        BACKUP_TAKE.matches(entry.name) ->
                            File(tracks, entry.name.substringAfter('/')).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val source = checkNotNull(json)
            check(ProjectJson.formatVersion(source) <= ProjectJson.FORMAT_VERSION)
            ProjectJson.decode(source).copy(id = id).also { imported ->
                // Every take and reference the project names must be a plain file of the backup.
                check(imported.tracks.flatMap { listOfNotNull(it.fileName, it.referenceFileName) }.all { name ->
                    TAKE_NAME.matches(name) && File(tracks, name).isFile
                })
            }
        }.getOrNull()
        if (project == null) {
            dir.deleteRecursively()
            return null
        }
        save(project)
        return project
    }

    /** A converted audio file ([file] in the internal WAV format) that becomes a track named [name]. */
    data class ImportedTake(val name: String, val file: File, val frames: Long, val channels: Int = 1)

    /**
     * Creates a project from converted audio files, one track each at project frame 0, in the given
     * order. The files are moved into the project; `project.json` is written last, so an import
     * that fails half-way never shows up as a project.
     */
    @Synchronized
    fun createFromTakes(name: String, sampleRate: Int, takes: List<ImportedTake>, nowMillis: Long): Project {
        var id = nowMillis
        while (projectDir(id).exists()) id++
        val dir = File(projectDir(id), TRACKS_DIR).apply { mkdirs() }
        try {
            val tracks = takes.mapIndexed { index, take ->
                val trackId = nowMillis + index
                val target = File(dir, "$trackId.wav")
                if (!take.file.renameTo(target)) {
                    take.file.copyTo(target, overwrite = true)
                    take.file.delete()
                }
                Track(
                    id = trackId,
                    name = take.name,
                    fileName = target.name,
                    offsetFrames = 0,
                    lengthFrames = take.frames,
                    channels = take.channels,
                    createdAtMillis = nowMillis,
                    effects = TrackEffects.IMPORTED
                )
            }
            return Project(
                id = id, name = name, createdAtMillis = nowMillis, sampleRate = sampleRate,
                nextTrackNumber = tracks.size + 1, tracks = tracks
            ).also(::save)
        } catch (error: Throwable) {
            projectDir(id).deleteRecursively()
            throw error
        }
    }

    /**
     * Removes files of takes that are not part of the project, e.g. a take interrupted by process
     * death. Only call while no take is being recorded.
     */
    @Synchronized
    fun deleteOrphanedFiles(projectId: Long) {
        val project = load(projectId) ?: return
        val prefixes = project.tracks.map { File(it.fileName).nameWithoutExtension + "." }
        tracksDir(projectId).listFiles { file -> prefixes.none { file.name.startsWith(it) } }?.forEach { it.delete() }
    }

    fun trackFile(projectId: Long, track: Track) = File(tracksDir(projectId), track.fileName)

    fun referenceFile(projectId: Long, track: Track): File? =
        track.referenceFileName?.let { File(tracksDir(projectId), it) }

    /** Cleaned take (click and bleed removed); the version invalidates caches when the algorithms change. */
    fun cleanCacheFile(projectId: Long, track: Track) =
        File(tracksDir(projectId), "${File(track.fileName).nameWithoutExtension}.clean-v$CLEAN_CACHE_VERSION.wav")

    private fun tracksDir(projectId: Long) = File(projectDir(projectId), TRACKS_DIR)

    private fun update(id: Long, change: (Project) -> Project): Project? =
        load(id)?.let(change)?.also(::save)

    private fun projectDir(id: Long) = File(projectsDir, id.toString())

    private fun save(project: Project) {
        val dir = projectDir(project.id).apply { mkdirs() }
        // Write-then-rename keeps the previous state if the app dies mid-write.
        val temp = File(dir, "$PROJECT_FILE.tmp")
        temp.writeText(ProjectJson.encode(project))
        val target = File(dir, PROJECT_FILE)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    companion object {
        /** Marks a ZIP as a backup; a ZIP without it is imported as audio files. */
        const val PROJECT_FILE = "project.json"
        private const val TRACKS_DIR = "tracks"
        private const val CLEAN_CACHE_VERSION = 4
        // Take and reference names as the app writes them, e.g. "1793307503200.ref.wav".
        private val TAKE_NAME = Regex("""[0-9]+(\.[A-Za-z0-9-]+)*\.wav""")
        private val BACKUP_TAKE = Regex("$TRACKS_DIR/${TAKE_NAME.pattern}")

        /** Reference file written next to a take while recording over playback. */
        fun referenceFileFor(take: File) = File(take.parentFile, "${take.nameWithoutExtension}.ref.wav")

        @Volatile
        private var instance: ProjectRepository? = null

        /** The app-wide repository below [root] (Android: `filesDir`; desktop: the user's project folder). */
        fun forRoot(root: File): ProjectRepository =
            instance ?: synchronized(this) {
                instance ?: ProjectRepository(root).also { instance = it }
            }
    }
}
