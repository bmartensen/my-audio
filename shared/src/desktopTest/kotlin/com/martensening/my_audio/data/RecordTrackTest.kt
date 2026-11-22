package com.martensening.my_audio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The record track: always the last track, empty or holding the latest take, with retakes over the same settings. */
class RecordTrackTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository by lazy { ProjectRepository(folder.root) }
    private val chain = listOf(EffectSlot(ChainEffect.Reverb()))

    private fun take(project: Project, name: String = "Track ${project.nextTrackNumber}"): Track {
        val file = repository.newTrackFile(project.id, System.nanoTime()).apply { writeBytes(ByteArray(100)) }
        ProjectRepository.referenceFileFor(file).writeBytes(ByteArray(100))
        return Track(
            id = file.nameWithoutExtension.toLong(), name = name, fileName = file.name, offsetFrames = -1000,
            lengthFrames = 48000, createdAtMillis = 1, muted = true
        )
    }

    @Test
    fun aNewProjectRecordsIntoAFreshMutedRecordTrack() {
        val project = repository.create("Demo", 48000, 1)
        assertNull(project.recordTrack)
        assertEquals(RecordTrack(), project.nextTake)
        assertTrue(project.nextTake.muted)
        assertTrue(project.nextTake.isPristine)
    }

    @Test
    fun aTakeUsesTheRecordTrackSettingsAndIsUnmuted() {
        var project = repository.create("Demo", 48000, 1)
        project = repository.updateRecordTrack(project.id, RecordTrack(name = "Gesang", volume = 0.7f, solo = true, effects = TrackEffects(chain = chain)))!!
        project = repository.addTake(project.id, take(project))!!
        val track = project.tracks.single()
        assertEquals("Gesang", track.name)
        assertEquals(0.7f, track.volume)
        assertTrue(track.solo)
        assertFalse(track.muted)
        assertEquals(chain, track.effects.chain)
        assertNull(project.recordTrack)
        assertEquals(2, project.nextTrackNumber)
    }

    @Test
    fun recordingAfterATakeAddsATrackWithItsEffectsButStartsMuted() {
        var project = repository.create("Demo", 48000, 1)
        project = repository.updateRecordTrack(project.id, RecordTrack(volume = 0.5f, pan = -1f, effects = TrackEffects(chain = chain)))!!
        project = repository.addTake(project.id, take(project))!!
        val next = project.nextTake
        assertEquals(chain, next.effects.chain)
        assertEquals(RecordTrack(effects = next.effects), next)
        project = repository.addTake(project.id, take(project))!!
        assertEquals(listOf("Track 1", "Track 2"), project.tracks.map { it.name })
        assertEquals(chain, project.tracks[1].effects.chain)
        assertEquals(1f, project.tracks[1].volume)
        assertFalse(project.tracks[1].muted)
    }

    @Test
    fun deletingTheRecordingKeepsAllSettingsForTheRetake() {
        var project = repository.create("Demo", 48000, 1)
        project = repository.updateRecordTrack(project.id, RecordTrack(name = "Gitarre", effects = TrackEffects(chain = chain)))!!
        project = repository.addTake(project.id, take(project))!!
        val track = project.tracks.single().copy(volume = 0.4f, volumeEnvelope = listOf(VolumePoint(0, 0.5f)))
        project = repository.updateTrack(project.id, track)!!
        val tracksDir = repository.trackFile(project.id, track).parentFile

        project = repository.deleteRecording(project.id, track.id)!!
        assertTrue(project.tracks.isEmpty())
        assertTrue(tracksDir.listFiles().orEmpty().isEmpty())
        val kept = project.recordTrack!!
        assertEquals(RecordTrack.keepingSettingsOf(track), kept)
        // The take had been unmuted; that stays until changed or reset.
        assertFalse(kept.muted)
        assertFalse(kept.isPristine)
        assertEquals(project, repository.load(project.id))

        project = repository.addTake(project.id, take(project, name = "ignored"))!!
        assertEquals("Gitarre", project.tracks.single().name)
        assertEquals(0.4f, project.tracks.single().volume)
        assertEquals(chain, project.tracks.single().effects.chain)
    }

    @Test
    fun onlyTheLastTakeLosesJustItsRecording() {
        var project = repository.create("Demo", 48000, 1)
        project = repository.addTake(project.id, take(project))!!
        project = repository.addTake(project.id, take(project))!!
        val first = project.tracks.first()
        assertEquals(project, repository.deleteRecording(project.id, first.id))
        project = repository.addEmptyTrack(project.id)!!
        // With an empty record track after it, the last take is an ordinary track.
        assertEquals(project, repository.deleteRecording(project.id, project.tracks.last().id))
    }

    @Test
    fun resetRestoresAFreshRecordTrack() {
        var project = repository.create("Demo", 48000, 1)
        project = repository.updateRecordTrack(project.id, RecordTrack(name = "X", muted = false, solo = true, pan = 1f, effects = TrackEffects(chain = chain)))!!
        project = repository.resetRecordTrack(project.id)!!
        assertEquals(RecordTrack(), project.recordTrack)
        assertTrue(project.nextTake.isPristine)
    }

    @Test
    fun anEmptyTrackIsOnlyAddedAfterATake() {
        var project = repository.create("Demo", 48000, 1)
        assertNull(repository.addEmptyTrack(project.id)!!.recordTrack)
        project = repository.addTake(project.id, take(project))!!
        project = repository.addEmptyTrack(project.id)!!
        assertEquals(RecordTrack(), project.recordTrack)
        // Its effects are its own, not inherited from the take before it.
        assertEquals(RecordTrack(), project.nextTake)
    }

    @Test
    fun aSoloOnTheEmptyRecordTrackSilencesTheOthers() {
        val track = Track(2, "A", "2.wav", 0, 48000, createdAtMillis = 2)
        val project = Project(1, "Demo", 1, 48000, tracks = listOf(track), recordTrack = RecordTrack(solo = true))
        assertTrue(project.audibleTracks.isEmpty())
        assertFalse(project.isAudible(track))
        assertEquals(listOf(track), project.copy(recordTrack = RecordTrack()).audibleTracks)
    }
}
