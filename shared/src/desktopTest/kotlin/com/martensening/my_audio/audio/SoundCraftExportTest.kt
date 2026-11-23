package com.martensening.my_audio.audio

import com.martensening.my_audio.data.ProjectRepository
import kotlinx.coroutines.runBlocking
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
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File = kotlin.io.path.createTempDirectory("soundcraft-export").toFile()
}
