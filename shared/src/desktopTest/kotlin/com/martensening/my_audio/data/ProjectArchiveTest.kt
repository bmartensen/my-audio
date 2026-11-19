package com.martensening.my_audio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ProjectArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository by lazy { ProjectRepository(folder.root.resolve("app").apply { mkdirs() }) }

    private fun track(id: Long, reference: Boolean = false) = Track(
        id = id,
        name = "Spur $id",
        fileName = "$id.wav",
        offsetFrames = -1020,
        lengthFrames = 48_000,
        createdAtMillis = id,
        referenceFileName = if (reference) "$id.ref.wav" else null
    )

    private fun projectWithTakes(): Project {
        val project = repository.create("Bruder Jakob", 48_000, nowMillis = 1_000)
        repository.newTrackFile(project.id, 10).writeText("take 10")
        repository.newTrackFile(project.id, 20).apply {
            writeText("take 20")
            ProjectRepository.referenceFileFor(this).writeText("reference 20")
            // A derived cache never goes into a backup.
            File(parentFile, "20.clean-v2.wav").writeText("cache")
        }
        repository.addTrack(project.id, track(10))
        return repository.addTrack(project.id, track(20, reference = true).copy(solo = true, pan = 0.5f))!!
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun backupContainsProjectTakesAndReferencesButNoCaches() {
        val project = projectWithTakes()
        val backup = folder.newFile("backup.zip")
        assertTrue(repository.exportBackup(project.id, backup))

        val names = ZipFile(backup).use { zip -> zip.entries().toList().map { it.name } }
        assertEquals(listOf("project.json", "tracks/10.wav", "tracks/20.wav", "tracks/20.ref.wav"), names)
    }

    @Test
    fun importRestoresTheProjectUnderANewId() {
        val project = projectWithTakes()
        val backup = folder.newFile("backup.zip")
        repository.exportBackup(project.id, backup)

        val imported = backup.inputStream().use { repository.importBackup(it, nowMillis = 1_000) }!!
        assertNotEquals(project.id, imported.id)
        assertEquals(project.copy(id = imported.id), imported)
        assertEquals(imported, repository.load(imported.id))
        assertEquals("reference 20", repository.referenceFile(imported.id, imported.tracks[1])!!.readText())
        assertEquals(2, repository.list().size)
    }

    @Test
    fun importRejectsBackupsWithMissingTakesAndLeavesNothingBehind() {
        val project = projectWithTakes()
        val json = folder.root.resolve("app/projects/${project.id}/project.json").readText()
        val incomplete = zip("project.json" to json, "tracks/10.wav" to "take 10")

        assertNull(repository.importBackup(ByteArrayInputStream(incomplete), nowMillis = 5_000))
        assertEquals(listOf(project.id), repository.list().map { it.id })
    }

    @Test
    fun importNeverWritesOutsideTheProjectAndRejectsForeignNames() {
        val json = """{"version":1,"id":1,"name":"Evil","createdAtMillis":1,"sampleRate":48000,
            "tracks":[{"id":2,"name":"x","fileName":"../../escape.wav","offsetFrames":0,"lengthFrames":1,"createdAtMillis":2}]}"""
        val evil = zip("project.json" to json, "../../escape.wav" to "x", "tracks/../../escape.wav" to "x")

        assertNull(repository.importBackup(ByteArrayInputStream(evil), nowMillis = 5_000))
        assertFalse(folder.root.resolve("escape.wav").exists() || folder.root.resolve("app/escape.wav").exists())
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun importRejectsNewerFormatsAndNonBackups() {
        val newer = zip("project.json" to """{"version":4,"id":1,"name":"Neu","createdAtMillis":1,"sampleRate":48000,"tracks":[]}""")
        assertNull(repository.importBackup(ByteArrayInputStream(newer), nowMillis = 5_000))
        assertNull(repository.importBackup(ByteArrayInputStream("no zip".toByteArray()), nowMillis = 5_000))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun fileNamesAreSafeAndStemsKeepTheTrackOrder() {
        assertEquals("Bruder Jakob - Projekt.zip", ProjectArchive.fileName("Bruder Jakob", ProjectArchive.Kind.Backup))
        assertEquals("A_B.ogg", ProjectArchive.fileName("A/B", ProjectArchive.Kind.Mix))
        assertEquals(
            listOf("01 Gesang.wav", "02 Git_rre_.wav"),
            ProjectArchive.stemNames(listOf(track(1).copy(name = "Gesang"), track(2).copy(name = "Git?rre*")))
        )
    }

    @Test
    fun picksAudioFilesOfAZipInTrackOrder() {
        val entries = listOf(
            "8 Other.mp3", "1 Backing Vocals.mp3", "0 Lead Vocals.mp3", "10 Extra.wav",
            "Song/", "cover.jpg", "readme.txt", "__MACOSX/._0 Lead Vocals.mp3", "Song/.hidden.mp3",
            "Song/Intro.OGG", "Ambience.opus"
        )
        assertEquals(
            listOf("0 Lead Vocals.mp3", "1 Backing Vocals.mp3", "8 Other.mp3", "10 Extra.wav", "Ambience.opus", "Song/Intro.OGG"),
            ProjectArchive.audioEntries(entries)
        )
        assertTrue(ProjectArchive.audioEntries(listOf("project.txt", "notes.md")).isEmpty())
    }

    @Test
    fun namesTracksWithoutFolderExtensionAndTrackNumber() {
        assertEquals("Lead Vocals", ProjectArchive.trackName("0 Lead Vocals.mp3"))
        assertEquals("Gesang", ProjectArchive.trackName("Export/01 Gesang.wav"))
        assertEquals("Bass", ProjectArchive.trackName("3 - Bass.ogg"))
        assertEquals("Synth", ProjectArchive.trackName("4_Synth.mp3"))
        // More than two digits are part of the name, and a bare number stays a name.
        assertEquals("808 Kick", ProjectArchive.trackName("808 Kick.wav"))
        assertEquals("2024 Demo", ProjectArchive.trackName("2024 Demo.mp3"))
        assertEquals("7", ProjectArchive.trackName("7.mp3"))
        assertNull(ProjectArchive.trackName(".mp3"))
    }

    @Test
    fun ownStemsComeBackWithTheirTrackNames() {
        val names = ProjectArchive.stemNames(listOf(track(1).copy(name = "Gesang"), track(2).copy(name = "Gitarre")))
        assertEquals(listOf("Gesang", "Gitarre"), ProjectArchive.audioEntries(names.reversed()).map { ProjectArchive.trackName(it) })
    }
}
