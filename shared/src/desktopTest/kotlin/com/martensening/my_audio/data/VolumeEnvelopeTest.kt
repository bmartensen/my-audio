package com.martensening.my_audio.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VolumeEnvelopeTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun missingEnvelopeInVersionOneMeansFullVolume() {
        val repository = ProjectRepository(folder.root)
        val project = repository.create("Old demo", 48000, 123)
        repository.addTrack(project.id, Track(1, "Voice", "1.wav", -1020, 48000, createdAtMillis = 123))
        val file = folder.root.resolve("projects/123/project.json")
        val json = org.json.JSONObject(file.readText())
        json.getJSONArray("tracks").getJSONObject(0).remove("volumeEnvelope")
        file.writeText(json.toString())
        assertTrue(repository.load(project.id)!!.tracks.single().volumeEnvelope.isEmpty())
    }

    @Test fun roundTripPreservesProjectFramesGainsAndExistingEffects() {
        val repository = ProjectRepository(folder.root)
        val project = repository.create("Demo", 48000, 123)
        val points = listOf(VolumePoint(0, 0f), VolumePoint(48000, 1f), VolumePoint(96000, 0.25f))
        val track = Track(1, "Voice", "1.wav", -1020, 100000, createdAtMillis = 123,
            effects = TrackEffects(chain = listOf(EffectSlot(ChainEffect.Reverb()))), volumeEnvelope = points)
        repository.addTrack(project.id, track)
        assertEquals(track, repository.load(project.id)!!.tracks.single())
        // An effect chain needs format version 3.
        assertEquals(3, org.json.JSONObject(folder.root.resolve("projects/123/project.json").readText()).getInt("version"))
    }

    @Test fun invalidPointsCannotIntroduceNanOrDuplicateTimesIntoNativeMix() {
        val points = listOf(VolumePoint(50, 0.5f), VolumePoint(0, 2f), VolumePoint(50, 0.2f),
            VolumePoint(-1, 1f), VolumePoint(80, Float.NaN), VolumePoint(90, Float.POSITIVE_INFINITY), VolumePoint(101, 0f))
        assertEquals(listOf(VolumePoint(0, 1f), VolumePoint(50, 0.5f)), VolumeEnvelope.sanitize(points, 100))
    }
}
