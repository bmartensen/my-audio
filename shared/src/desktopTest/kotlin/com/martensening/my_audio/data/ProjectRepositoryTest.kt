package com.martensening.my_audio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository by lazy { ProjectRepository(folder.root) }

    private fun track(id: Long, fileName: String = "$id.wav") = Track(
        id = id,
        name = "Track $id",
        fileName = fileName,
        offsetFrames = -1020,
        lengthFrames = 48_000,
        createdAtMillis = id
    )

    @Test
    fun createsProjectInOwnDirectoryAndListsNewestFirst() {
        val older = repository.create("Older", 48_000, nowMillis = 1_000)
        val newer = repository.create("Newer", 48_000, nowMillis = 2_000)

        assertTrue(folder.root.resolve("projects/${older.id}/project.json").isFile)
        assertTrue(folder.root.resolve("projects/${older.id}/tracks").isDirectory)
        assertEquals(listOf("Newer", "Older"), repository.list().map { it.name })
        assertNotEquals(older.id, newer.id)
    }

    @Test
    fun keepsIdsUniqueForTheSameTimestamp() {
        val first = repository.create("A", 48_000, nowMillis = 5_000)
        val second = repository.create("B", 48_000, nowMillis = 5_000)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun roundTripsTracksAndCountsTrackNumbers() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        repository.addTrack(project.id, track(10))
        repository.addTrack(project.id, track(11).copy(muted = true, volume = 0.5f))

        val loaded = repository.load(project.id)!!
        assertEquals(listOf(track(10), track(11).copy(muted = true, volume = 0.5f)), loaded.tracks)
        assertEquals(3, loaded.nextTrackNumber)
        assertEquals(48_000L - 1020, loaded.lengthFrames)
    }

    @Test
    fun notesScrollAndPreRollAreOffByDefaultAndRoundTrip() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        val fresh = repository.load(project.id)!!
        assertEquals(NotesScroll(), fresh.notesScroll)
        assertEquals(PreRoll(), fresh.preRoll)
        assertFalse(fresh.notesScroll.enabled)
        assertFalse(fresh.preRoll.enabled)

        repository.setNotesScroll(project.id, NotesScroll(enabled = true, secondsPerLine = 2.5f))
        repository.setPreRoll(project.id, PreRoll(enabled = true, seconds = 8))
        val loaded = repository.load(project.id)!!
        assertEquals(NotesScroll(enabled = true, secondsPerLine = 2.5f), loaded.notesScroll)
        assertEquals(PreRoll(enabled = true, seconds = 8), loaded.preRoll)
    }

    @Test
    fun clampsNotesScrollAndPreRollFromForeignFiles() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        val file = folder.root.resolve("projects/${project.id}/project.json")
        val json = org.json.JSONObject(file.readText())
            .put("notesScroll", org.json.JSONObject().put("enabled", true).put("secondsPerLine", 99.0))
            .put("preRoll", org.json.JSONObject().put("enabled", true).put("seconds", -3))
        file.writeText(json.toString())
        val loaded = repository.load(project.id)!!
        assertEquals(NotesScroll.MAX_SECONDS_PER_LINE, loaded.notesScroll.secondsPerLine)
        assertEquals(PreRoll.MIN_SECONDS, loaded.preRoll.seconds)
    }

    @Test
    fun metronomeIsOffByDefaultAndRoundTrips() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        assertFalse(repository.load(project.id)!!.metronome.enabled)

        val metronome = Metronome(enabled = true, bpm = 120, volume = 0.3f)
        repository.setMetronome(project.id, metronome)
        repository.addTrack(project.id, track(12).copy(recordedMetronome = metronome))

        val loaded = repository.load(project.id)!!
        assertEquals(metronome, loaded.metronome)
        assertEquals(metronome, loaded.tracks.single().recordedMetronome)
    }

    @Test
    fun effectsDefaultToMetronomeRemovalOnlyAndRoundTrip() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        repository.addTrack(project.id, track(20))
        assertEquals(TrackEffects(), repository.load(project.id)!!.tracks.single().effects)

        val effects = TrackEffects(
            removeMetronome = false,
            normalize = NormalizeEffect(enabled = true, targetDb = -3f),
            chain = listOf(EffectSlot(ChainEffect.Reverb(ReverbEffect().with(ReverbEffect.Preset.Cathedral).copy(seconds = 8.5f))))
        )
        repository.updateTrack(project.id, track(20).copy(effects = effects))
        assertEquals(effects, repository.load(project.id)!!.tracks.single().effects)
    }

    @Test
    fun newProjectsUseQuietMetronomeDefaults() {
        val metronome = repository.create("Demo", 48_000, nowMillis = 1_000).metronome
        assertEquals(Metronome(enabled = false, bpm = 120, volume = 0.15f, duringPlayback = false), metronome)
    }

    @Test
    fun countInIsOneBarOnTheClickGrid() {
        assertEquals(96_000L, Metronome(bpm = 120).countInFrames(48_000))
        // Native grid: beatStart(-4) = ceil(-4 * 26181.81...) = -104727.
        assertEquals(104_727L, Metronome(bpm = 110).countInFrames(48_000))
    }

    @Test
    fun countInRoundTrips() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        repository.addTrack(project.id, track(30).copy(countInFrames = 96_000))
        assertEquals(96_000L, repository.load(project.id)!!.tracks.single().countInFrames)
    }

    @Test
    fun renamesProject() {
        val project = repository.create("Old", 48_000, nowMillis = 1_000)
        repository.rename(project.id, "New")
        assertEquals("New", repository.load(project.id)!!.name)
    }

    @Test
    fun deletedTrackKeepsItsFileForUndoUntilTheProjectIsClosed() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        val file = repository.newTrackFile(project.id, nowMillis = 42).apply { writeText("pcm") }
        val before = repository.addTrack(project.id, track(42, file.name))!!

        val updated = repository.deleteTrack(project.id, 42)!!
        assertTrue(updated.tracks.isEmpty())
        assertTrue(file.exists())
        // Undo writes the earlier state back; the take is still there.
        assertEquals(before, repository.restore(before))
        assertEquals(before, repository.load(project.id))

        repository.deleteTrack(project.id, 42)
        repository.deleteOrphanedFiles(project.id)
        assertFalse(file.exists())
    }

    @Test
    fun panEqAndCompressorRoundTripAndPresetsSetBoth() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        val tone = ChainEffect.Tone().with(TonePreset.Vocals)
        val effects = TrackEffects(chain = listOf(EffectSlot(tone)))
        repository.addTrack(project.id, track(10).copy(pan = -0.4f, effects = effects))

        val loaded = repository.load(project.id)!!.tracks.single()
        assertEquals(-0.4f, loaded.pan, 1e-6f)
        val loadedTone = loaded.effects.chain.single().effect as ChainEffect.Tone
        assertEquals(EqEffect(true, -3f, 3f, 2f), loadedTone.eq)
        assertEquals(CompressorEffect(true, 0.5f), loadedTone.compressor)
        assertEquals(TonePreset.Vocals, loadedTone.preset)
        // New tracks keep the dry sound.
        assertTrue(TrackEffects().chain.isEmpty())
    }

    @Test
    fun soloAndLoopRoundTrip() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        repository.addTrack(project.id, track(10).copy(solo = true))
        repository.setLoop(project.id, PlaybackLoop(4_800, 96_000, enabled = false))

        val loaded = repository.load(project.id)!!
        assertTrue(loaded.tracks.single().solo)
        assertEquals(PlaybackLoop(4_800, 96_000, enabled = false), loaded.loop)
        assertNull(repository.setLoop(project.id, null)!!.loop)
        assertNull(repository.load(project.id)!!.loop)
    }

    @Test
    fun orphanedTakeFilesAreRemovedButProjectTracksKept() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        val kept = repository.newTrackFile(project.id, nowMillis = 10).apply { writeText("pcm") }
        repository.addTrack(project.id, track(10, kept.name))
        val orphan = repository.newTrackFile(project.id, nowMillis = 20).apply { writeText("pcm") }
        val orphanReference = ProjectRepository.referenceFileFor(orphan).apply { writeText("ref") }

        repository.deleteOrphanedFiles(project.id)
        assertTrue(kept.exists())
        assertFalse(orphan.exists() || orphanReference.exists())
    }

    @Test
    fun soloOverridesMuteAndLoopMustLieInsideTheProject() {
        val project = Project(1, "Demo", 1, 48_000, tracks = listOf(
            track(1), track(2).copy(muted = true), track(3).copy(solo = true, muted = true)
        ))
        assertEquals(listOf(3L), project.audibleTracks.map { it.id })
        assertTrue(project.isAudible(project.tracks[2]) && !project.isAudible(project.tracks[0]))
        val unsoloed = project.copy(tracks = project.tracks.map { it.copy(solo = false) })
        assertEquals(listOf(1L), unsoloed.audibleTracks.map { it.id })

        assertEquals(PlaybackLoop(0, 24_000), project.copy(loop = PlaybackLoop(0, 24_000)).activeLoop)
        assertNull(project.copy(loop = PlaybackLoop(0, 24_000, enabled = false)).activeLoop)
        assertNull(project.copy(loop = PlaybackLoop(0, project.lengthFrames + 1)).activeLoop)
    }

    @Test
    fun deletingProjectRemovesDirectory() {
        val project = repository.create("Demo", 48_000, nowMillis = 1_000)
        repository.newTrackFile(project.id, nowMillis = 7).writeText("pcm")

        repository.delete(project.id)
        assertFalse(folder.root.resolve("projects/${project.id}").exists())
        assertNull(repository.load(project.id))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun createsProjectFromImportedTakesAtFrameZero() {
        val sources = folder.newFolder("import")
        val takes = listOf("Lead Vocals" to 1_000L, "Drums" to 2_000L).mapIndexed { index, (name, frames) ->
            ProjectRepository.ImportedTake(name, sources.resolve("$index.wav").apply { writeText("take $index") }, frames)
        }

        val project = repository.createFromTakes("Das Geschenk", 48_000, takes, nowMillis = 7_000)

        val loaded = repository.load(project.id)!!
        assertEquals("Das Geschenk", loaded.name)
        assertEquals(listOf("Lead Vocals", "Drums"), loaded.tracks.map { it.name })
        assertEquals(listOf(1_000L, 2_000L), loaded.tracks.map { it.lengthFrames })
        assertTrue(loaded.tracks.all { it.offsetFrames == 0L && it.referenceFileName == null && it.recordedMetronome == null })
        // Nothing to remove without a recorded click or playback reference: both switches are off.
        assertTrue(loaded.tracks.all { !it.effects.removeBleed && !it.effects.removeMetronome })
        assertEquals(3, loaded.nextTrackNumber)
        assertEquals(listOf("take 0", "take 1"), loaded.tracks.map { repository.trackFile(project.id, it).readText() })
        assertTrue(sources.listFiles()!!.isEmpty())
    }
}
