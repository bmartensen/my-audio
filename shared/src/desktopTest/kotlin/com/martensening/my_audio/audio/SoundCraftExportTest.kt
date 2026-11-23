package com.martensening.my_audio.audio

import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.SoundCraftImport
import com.martensening.my_audio.data.SoundCraftSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * End-to-end SoundCraft export through the native engine. Runs only with `MYAUDIO_NATIVE_DIR` (the folder of the
 * built desktop library) and `MYAUDIO_SOUNDCRAFT_PROJECT` (a My Audio backup ZIP, e.g. from tools/demo_project.py);
 * writes the session ZIP to `MYAUDIO_SOUNDCRAFT_OUT` or the build folder, so SoundCraft can open or bounce it.
 */
class SoundCraftExportTest {
    @Test
    fun exportsABackupAsASoundCraftSession() {
        val native = System.getenv("MYAUDIO_NATIVE_DIR")
        val backup = System.getenv("MYAUDIO_SOUNDCRAFT_PROJECT")?.let(::File)
        assumeTrue(native != null && backup != null && backup.isFile)
        System.setProperty("myaudio.native.dir", native!!)
        val root = createTempDirectory()
        try {
            val repository = ProjectRepository(root)
            val project = backup!!.inputStream().use { repository.importBackup(it, 1) }
            assertTrue("backup imports", project != null)
            val target = System.getenv("MYAUDIO_SOUNDCRAFT_OUT")?.let(::File)
                ?: File("build/soundcraft-export-test/${project!!.name} - SoundCraft.zip")
            target.parentFile.mkdirs()
            assertTrue("export succeeds", runBlocking { SoundCraftExport.export(project!!, repository, target) })
            ZipFile(target).use { zip ->
                val names = zip.entries().toList().map { it.name }
                assertTrue(names.toString(), names.any { it.endsWith(".scraft") })
                assertTrue(names.toString(), names.count { it.contains("/Audio Files/") && it.endsWith(".wav") } >= project!!.tracks.size)
            }

            // Round trip: the session imports as a project with the same positions, levels and mapped effects ...
            val work = File(root, "import").apply { mkdirs() }
            val imported = ZipFile(target).use { zip ->
                runBlocking {
                    SoundCraftImport.import(zip, work, repository, 2, "Fallback") { _, _ -> error("48 kHz WAVs need no decoder") }
                }
            }
            assertTrue("session imports", imported != null)
            assertEquals(project!!.tracks.size, imported!!.tracks.size)
            for ((original, back) in project.tracks.zip(imported.tracks)) {
                assertEquals(original.name, back.name)
                assertEquals(original.name, maxOf(original.offsetFrames, 0L), back.offsetFrames)
                assertEquals(original.name, original.volume, back.volume, 0.01f)
                assertEquals(original.name, original.pan, back.pan, 0.01f)
                assertEquals(original.name, original.muted, back.muted)
                assertEquals(original.name, original.volumeEnvelope.size, back.volumeEnvelope.size)
                // Mappable effects come back as such; rendered ones stay in the audio.
                assertEquals(original.name, SoundCraftSession.split(original.effects.chain).second.map { it.effect::class },
                    back.effects.chain.map { it.effect::class })
            }
            assertEquals(project.metronome.bpm, imported.metronome.bpm)
            assertTrue(imported.notes.startsWith(project.notes.trim()))
            // ... and exports again.
            val again = File(target.parentFile, "${target.nameWithoutExtension} - again.zip")
            assertTrue("export again", runBlocking { SoundCraftExport.export(imported, repository, again) })
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * Imports a session ZIP that SoundCraft itself saved (`MYAUDIO_SOUNDCRAFT_SESSION`) and checks the track starts
     * against `MYAUDIO_SOUNDCRAFT_STARTS` (comma-separated frames in track order).
     */
    @Test
    fun importsASessionSavedBySoundCraft() {
        val session = System.getenv("MYAUDIO_SOUNDCRAFT_SESSION")?.let(::File)
        val starts = System.getenv("MYAUDIO_SOUNDCRAFT_STARTS")?.split(',')?.map { it.trim().toLong() }
        assumeTrue(session != null && session.isFile && starts != null)
        val root = createTempDirectory()
        try {
            val repository = ProjectRepository(root)
            val work = File(root, "import").apply { mkdirs() }
            val project = ZipFile(session!!).use { zip ->
                runBlocking { SoundCraftImport.import(zip, work, repository, 3, "Fallback") { _, _ -> error("no decoder") } }
            }
            assertTrue("session imports", project != null)
            assertEquals(starts, project!!.tracks.map { it.offsetFrames })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File = kotlin.io.path.createTempDirectory("soundcraft-export").toFile()
}
