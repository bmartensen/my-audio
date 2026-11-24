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
    private val tones = ToneLibrary.forRoot(root)

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

    /**
     * Saves a recorded [take] with the settings of [Project.nextTake], unmuted so it is heard right away. The
     * empty record track is used up; the new track is now the record track.
     */
    @Synchronized
    fun addTake(projectId: Long, take: Track): Project? = update(projectId) { project ->
        val settings = project.nextTake
        val track = take.copy(
            name = settings.name ?: take.name,
            volume = settings.volume,
            muted = false,
            solo = settings.solo,
            pan = settings.pan,
            effects = settings.effects,
            volumeEnvelope = settings.volumeEnvelope
        )
        project.copy(tracks = project.tracks + track, recordTrack = null, nextTrackNumber = project.nextTrackNumber + 1)
    }

    /**
     * Removes only the audio of the last track, which is the record track while no empty one follows it. Its
     * settings stay as the empty record track, so the next take runs through the same effects. The files stay
     * until [deleteOrphanedFiles], so undo can bring the take back while the project is open.
     */
    @Synchronized
    fun deleteRecording(projectId: Long, trackId: Long): Project? {
        val project = load(projectId) ?: return null
        val track = project.tracks.lastOrNull()?.takeIf { it.id == trackId && project.recordTrack == null } ?: return project
        return project.copy(tracks = project.tracks - track, recordTrack = RecordTrack.keepingSettingsOf(track)).also(::save)
    }

    /** Removes the prepared empty track and its settings without touching any recorded tracks. */
    @Synchronized
    fun deleteRecordTrack(projectId: Long): Project? = update(projectId) { it.copy(recordTrack = null) }

    /** Adds an empty record track after the last take, to prepare the next instrument before recording. */
    @Synchronized
    fun addEmptyTrack(projectId: Long): Project? = update(projectId) { project ->
        if (project.recordTrack != null) project else project.copy(recordTrack = RecordTrack())
    }

    @Synchronized
    fun updateRecordTrack(projectId: Long, record: RecordTrack): Project? = update(projectId) { it.copy(recordTrack = record) }

    @Synchronized
    fun setMetronome(projectId: Long, metronome: Metronome): Project? = update(projectId) { it.copy(metronome = metronome) }

    @Synchronized
    fun updateTrack(projectId: Long, track: Track): Project? = update(projectId) { project ->
        project.copy(tracks = project.tracks.map { if (it.id == track.id) track else it })
    }

    /**
     * Writes [project] as it is, e.g. a state from the undo history; its takes must still be on disk (deleted takes
     * stay until the project is closed).
     */
    @Synchronized
    fun restore(project: Project): Project? {
        if (load(project.id) == null) return null
        save(project)
        return project
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

    /** Removes a track from the project; its files stay until [deleteOrphanedFiles], like [deleteRecording]. */
    @Synchronized
    fun deleteTrack(projectId: Long, trackId: Long): Project? {
        val project = load(projectId) ?: return null
        val track = project.tracks.firstOrNull { it.id == trackId } ?: return project
        return project.copy(tracks = project.tracks - track).also(::save)
    }

    /**
     * Writes the project as a ZIP backup: `project.json` with the takes and their references, and the imported
     * tones its effect chains use under `tones/`. Derived caches are left out and rebuilt after an import.
     */
    @Synchronized
    fun exportBackup(projectId: Long, target: File): Boolean {
        val project = load(projectId) ?: return false
        val takes = project.tracks.flatMap { listOfNotNull(it.fileName, it.referenceFileName) }
            .map { "$TRACKS_DIR/$it" to File(tracksDir(projectId), it) }
            .filter { it.second.isFile }
        val toneFiles = ToneLibrary.usedBy(project).keys.mapNotNull(tones::find)
            .map { "${ToneLibrary.DIR}/${it.hash}.${it.kind.extension}" to tones.file(it) }
            .filter { it.second.isFile }
        ProjectArchive.writeZip(target, listOf(PROJECT_FILE to File(projectDir(projectId), PROJECT_FILE)) + takes + toneFiles)
        return true
    }

    /**
     * Imports a backup as a new project (new id, everything else kept). Only `project.json`, WAV files
     * directly below `tracks/` and tones below `tones/` (into the tone library) are unpacked; a backup from a newer
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
            val toneEntries = File(dir, "tones.import").apply { mkdirs() }
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == PROJECT_FILE -> json = zip.readBytes().decodeToString()
                        BACKUP_TAKE.matches(entry.name) ->
                            File(tracks, entry.name.substringAfter('/')).outputStream().use { zip.copyTo(it) }
                        BACKUP_TONE.matches(entry.name) ->
                            File(toneEntries, entry.name.substringAfter('/')).outputStream().use { zip.copyTo(it) }
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
                // Tones go into the library under the name their slot shows; a missing one leaves its slot silent.
                for ((hash, effect) in ToneLibrary.usedBy(imported)) {
                    val (kind, name) = when (effect) {
                        is ChainEffect.AmpModel -> ToneKind.AmpModel to effect.name
                        is ChainEffect.Cabinet -> ToneKind.Cabinet to effect.name
                        else -> continue
                    }
                    val file = File(toneEntries, "$hash.${kind.extension}")
                    if (file.isFile) file.inputStream().use { tones.restore(hash, kind, name, it) }
                }
                toneEntries.deleteRecursively()
            }
        }.getOrNull()
        if (project == null) {
            dir.deleteRecursively()
            return null
        }
        save(project)
        return project
    }

    /**
     * A converted audio file ([file] in the internal WAV format) that becomes a track named [name], starting at
     * project frame [offsetFrames]; [configure] sets further track settings (e.g. from a SoundCraft session).
     */
    data class ImportedTake(
        val name: String,
        val file: File,
        val frames: Long,
        val channels: Int = 1,
        val offsetFrames: Long = 0,
        val configure: (Track) -> Track = { it }
    )

    /**
     * Creates a project from converted audio files, one track each at its offset (usually project frame 0), in the
     * given order; [configure] completes the project. The files are moved into the project; `project.json` is
     * written last, so an import that fails half-way never shows up as a project.
     */
    @Synchronized
    fun createFromTakes(name: String, sampleRate: Int, takes: List<ImportedTake>, nowMillis: Long,
                        configure: (Project) -> Project = { it }): Project {
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
                    offsetFrames = take.offsetFrames,
                    lengthFrames = take.frames,
                    channels = take.channels,
                    createdAtMillis = nowMillis,
                    effects = TrackEffects.IMPORTED
                ).let(take.configure)
            }
            return Project(
                id = id, name = name, createdAtMillis = nowMillis, sampleRate = sampleRate,
                nextTrackNumber = tracks.size + 1, tracks = tracks
            ).let(configure).also(::save)
        } catch (error: Throwable) {
            projectDir(id).deleteRecursively()
            throw error
        }
    }

    /**
     * Removes files of takes that are not part of the project: deleted takes (kept for undo while the project was
     * open) and takes interrupted by process death. Take, reference and caches share the take's id as name prefix.
     * Only call while no take is being recorded.
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
        // The cleaned take depends on the latency (bleed alignment), which can change for uncalibrated takes.
        load(project.id)?.tracks?.forEach { old ->
            val new = project.tracks.firstOrNull { it.id == old.id } ?: return@forEach
            if (new.latency != old.latency) cleanCacheFile(project.id, new).delete()
        }
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
        /** Highest track volume an import may set; the track dialog offers the same (+6 dB). */
        const val MAX_IMPORTED_GAIN = 2f
        private const val TRACKS_DIR = "tracks"
        private const val CLEAN_CACHE_VERSION = 4
        // Take and reference names as the app writes them, e.g. "1793307503200.ref.wav".
        private val TAKE_NAME = Regex("""[0-9]+(\.[A-Za-z0-9-]+)*\.wav""")
        private val BACKUP_TAKE = Regex("$TRACKS_DIR/${TAKE_NAME.pattern}")
        private val BACKUP_TONE = Regex("""${ToneLibrary.DIR}/[0-9a-f]{64}\.(nam|wav)""")

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
