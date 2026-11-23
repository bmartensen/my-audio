package com.martensening.my_audio.audio

import com.martensening.my_audio.data.Project
import com.martensening.my_audio.data.ProjectArchive
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.SoundCraftSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Share → SoundCraft session": a ZIP with one folder holding `<name>.scraft` and `Audio Files/`. Each take is one
 * clip at its position; the part of its chain SoundCraft cannot rebuild is rendered into the audio, the dry take
 * becomes playlist „Dry", and My Audio's fixed master stage becomes a maximizer (see [SoundCraftSession]).
 */
object SoundCraftExport {
    private const val MASTER_LUFS = -14.0
    private const val MASTER_MAX_GAIN_DB = 24.0

    /** Writes the session ZIP to [target]; work files go next to it and are removed again. */
    suspend fun export(project: Project, repository: ProjectRepository, target: File): Boolean {
        val base = ProjectArchive.safeName(project.name)
        val dir = File(target.parentFile, "${target.nameWithoutExtension}.work")
        val audio = File(dir, SoundCraftSession.AUDIO_DIR)
        withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            audio.mkdirs()
        }
        try {
            val names = ProjectArchive.stemNames(project.tracks).map { it.removeSuffix(".wav") }
            val splits = project.tracks.map { SoundCraftSession.split(it.effects.chain) }
            val wet = names.map { File(audio, "$it.wav") }
            val dry = names.mapIndexed { index, name ->
                File(audio, if (splits[index].first.isEmpty()) "$name.wav" else "$name (Dry).wav")
            }
            val result = AudioEngine.exportDawTracks(project, repository, splits.map { it.first }, dry, wet) ?: return false
            val loudness = AudioEngine.mixLoudness(project, repository)
            // What My Audio's -14 LUFS stage adds (at most 24 dB); SoundCraft's maximizer is set to match.
            val masterGainDb = if (loudness.isFinite()) (MASTER_LUFS - loudness).coerceAtMost(MASTER_MAX_GAIN_DB) else 0.0
            val tracks = project.tracks.mapIndexedNotNull { index, track ->
                val dryFrames = result[index * 4 + 1]
                val wetFrames = result[index * 4 + 2]
                if (dryFrames <= 0) return@mapIndexedNotNull null
                SoundCraftSession.ExportedTrack(
                    track = track,
                    start = result[index * 4],
                    channels = result[index * 4 + 3].toInt(),
                    dryFile = dry[index].name,
                    dryFrames = dryFrames,
                    wetFile = wet[index].name.takeIf { wetFrames > 0 },
                    wetFrames = wetFrames,
                    inserts = splits[index].second
                )
            }
            withContext(Dispatchers.IO) {
                val session = File(dir, "$base.scraft").apply { writeText(SoundCraftSession.json(project, tracks, masterGainDb)) }
                val files = tracks.flatMap { listOfNotNull(it.dryFile, it.wetFile) }.map { File(audio, it) }
                ProjectArchive.writeZip(target, listOf("$base/${session.name}" to session) +
                    files.map { "$base/${SoundCraftSession.AUDIO_DIR}/${it.name}" to it })
            }
            return true
        } finally {
            withContext(Dispatchers.IO) { dir.deleteRecursively() }
        }
    }
}
