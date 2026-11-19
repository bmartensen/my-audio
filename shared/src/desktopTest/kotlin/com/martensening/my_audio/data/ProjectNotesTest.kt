package com.martensening.my_audio.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectNotesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun notesPreserveWhitespaceAndSurviveBackupImportWithoutTracks() {
        val repository = ProjectRepository(folder.root)
        val project = repository.create("Song", 48000, 1)
        val text = "  Am       G\nGrüße 🎵\n\n  Refrain  \n"
        repository.setNotes(project.id, text)
        assertEquals(text, repository.load(project.id)!!.notes)
        val zip = folder.newFile("song.zip")
        assertTrue(repository.exportBackup(project.id, zip))
        val restored = zip.inputStream().use { repository.importBackup(it, 2) }!!
        assertEquals(text, restored.notes)
    }

    @Test fun projectSettingsSurviveBackupImport() {
        val repository = ProjectRepository(folder.root)
        val project = repository.create("Song", 48000, 1)
        repository.setNotesTextSize(project.id, 24)
        repository.setNotesScroll(project.id, NotesScroll(enabled = true, secondsPerLine = 3.5f))
        repository.setPreRoll(project.id, PreRoll(enabled = true, seconds = 12))
        repository.setMetronome(project.id, Metronome(enabled = true, bpm = 96, volume = 0.2f, duringPlayback = true))
        repository.setLoop(project.id, PlaybackLoop(48_000, 96_000, enabled = false))
        val zip = folder.newFile("settings.zip")
        assertTrue(repository.exportBackup(project.id, zip))
        val restored = zip.inputStream().use { repository.importBackup(it, 2) }!!
        assertEquals(repository.load(project.id)!!.copy(id = restored.id), restored)
        assertEquals(24, restored.notesTextSize)
        assertEquals(NotesScroll(enabled = true, secondsPerLine = 3.5f), restored.notesScroll)
        assertEquals(PreRoll(enabled = true, seconds = 12), restored.preRoll)
    }

    @Test fun notesAndTakeChangesPreserveEachOtherAndNotesCanBeCleared() {
        val repository = ProjectRepository(folder.root)
        val project = repository.create("Song", 48000, 1)
        repository.setNotes(project.id, "Verse")
        val track = Track(2, "Guitar", "2.wav", 0, 48000, createdAtMillis = 2)
        repository.addTrack(project.id, track)
        repository.setNotes(project.id, "Chorus")
        repository.updateTrack(project.id, track.copy(muted = true))
        val loaded = repository.load(project.id)!!
        assertEquals("Chorus", loaded.notes)
        assertTrue(loaded.tracks.single().muted)
        assertEquals("", repository.setNotes(project.id, "")!!.notes)
    }

    @Test fun notesTextSizeIsKeptPerProjectAndClampedOnLoad() {
        val repository = ProjectRepository(folder.root)
        val song = repository.create("Song", 48000, 1)
        val other = repository.create("Other", 48000, 2)
        repository.setNotesTextSize(song.id, Project.MIN_NOTES_TEXT_SIZE)
        assertEquals(Project.MIN_NOTES_TEXT_SIZE, repository.load(song.id)!!.notesTextSize)
        assertEquals(Project.DEFAULT_NOTES_TEXT_SIZE, repository.load(other.id)!!.notesTextSize)
        repository.setNotesTextSize(song.id, 99)
        assertEquals(Project.MAX_NOTES_TEXT_SIZE, repository.load(song.id)!!.notesTextSize)
    }
}
