package com.martensening.my_audio.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Golden files for `project.json` format version 1. Projects written by today's app must stay
 * readable; if one of these tests breaks, add a migration instead of changing the fixture.
 */
class ProjectFormatTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun loadFixture(json: String): Project {
        val dir = folder.root.resolve("projects/1793307485396").apply { mkdirs() }
        dir.resolve("project.json").writeText(json)
        return ProjectRepository(folder.root).load(1793307485396)!!
    }

    @Test
    fun readsFullVersionOneProject() {
        val project = loadFixture(
            """
            {
              "version": 1,
              "id": 1793307485396,
              "name": "Demo",
              "createdAtMillis": 1793307485396,
              "sampleRate": 48000,
              "nextTrackNumber": 3,
              "metronome": {"enabled": true, "bpm": 96, "volume": 0.15, "duringPlayback": true},
              "tracks": [
                {
                  "id": 1793307503200,
                  "name": "Gesang",
                  "fileName": "1793307503200.wav",
                  "offsetFrames": -97020,
                  "lengthFrames": 703872,
                  "volume": 0.8,
                  "muted": true,
                  "createdAtMillis": 1793307520000,
                  "recordedMetronome": {"enabled": true, "bpm": 96, "volume": 0.15, "duringPlayback": false},
                  "countInFrames": 120000,
                  "referenceFileName": "1793307503200.ref.wav",
                  "effects": {
                    "removeMetronome": false,
                    "removeBleed": false,
                    "normalize": {"enabled": true, "targetDb": -3.5},
                    "reverb": {"enabled": true, "seconds": 7, "preDelayMs": 60, "damping": 0.3, "mix": 0.35}
                  }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("Demo", project.name)
        assertEquals("", project.notes)
        assertEquals(Project.DEFAULT_NOTES_TEXT_SIZE, project.notesTextSize)
        assertEquals(3, project.nextTrackNumber)
        assertEquals(Metronome(enabled = true, bpm = 96, volume = 0.15f, duringPlayback = true), project.metronome)
        val track = project.tracks.single()
        assertEquals(
            Track(
                id = 1793307503200,
                name = "Gesang",
                fileName = "1793307503200.wav",
                offsetFrames = -97020,
                lengthFrames = 703872,
                volume = 0.8f,
                muted = true,
                createdAtMillis = 1793307520000,
                recordedMetronome = Metronome(enabled = true, bpm = 96, volume = 0.15f, duringPlayback = false),
                countInFrames = 120000,
                referenceFileName = "1793307503200.ref.wav",
                effects = TrackEffects(
                    removeMetronome = false,
                    removeBleed = false,
                    normalize = NormalizeEffect(enabled = true, targetDb = -3.5f),
                    // The fixed reverb of version 1 becomes the equivalent „Hall (My Audio)“ slot.
                    chain = listOf(EffectSlot(ChainEffect.Reverb(ReverbEffect(seconds = 7f, preDelayMs = 60f, damping = 0.3f, mix = 0.35f))))
                )
            ),
            track
        )
    }

    @Test
    fun fillsDefaultsForFieldsAddedLater() {
        // The very first projects had neither metronome, effects, count-in nor reference.
        val project = loadFixture(
            """
            {
              "version": 1, "id": 1793307485396, "name": "Alt", "createdAtMillis": 1, "sampleRate": 48000,
              "tracks": [
                {"id": 2, "name": "Spur 1", "fileName": "2.wav", "offsetFrames": -1020, "lengthFrames": 48000,
                 "createdAtMillis": 2}
              ]
            }
            """.trimIndent()
        )

        assertEquals(Metronome(), project.metronome)
        assertEquals(NotesScroll(), project.notesScroll)
        assertEquals(PreRoll(), project.preRoll)
        assertEquals(2, project.nextTrackNumber)
        val track = project.tracks.single()
        assertEquals(1f, track.volume)
        assertEquals(false, track.muted)
        assertEquals(false, track.solo)
        assertEquals(null, project.loop)
        assertEquals(null, track.recordedMetronome)
        assertEquals(0L, track.countInFrames)
        assertEquals(null, track.referenceFileName)
        assertEquals(TrackEffects(removeMetronome = true, removeBleed = true), track.effects)
    }
}
