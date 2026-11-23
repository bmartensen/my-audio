package com.martensening.my_audio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amp model and cabinet slots (0.10): optional slot types within format 3, readable by 0.9.x as unknown slots. */
class ToneSlotFormatTest {
    private val hash = "01d57f1ad88d6203f8859bfc4296682e7a962baedbb5936f7107b6f4a8cc9ff0"

    @Test
    fun ampModelAndCabinetRoundTripWithoutRaisingTheFormat() {
        val chain = listOf(
            EffectSlot(ChainEffect.AmpModel(hash, "Super Reverb", inputDb = -3f, outputDb = 2.5f)),
            EffectSlot(ChainEffect.Cabinet(hash.reversed(), "Deluxe Oxford", levelMatch = false), enabled = false)
        )
        val project = Project(1, "Demo", 1, 48000, tracks = listOf(
            Track(2, "Gitarre", "2.wav", 0, 48000, createdAtMillis = 2, effects = TrackEffects(chain = chain))
        ))
        val text = ProjectJson.encode(project)
        assertEquals(3, ProjectJson.formatVersion(text))
        assertTrue("myaudio.nam" in text && "myaudio.ir" in text)
        assertEquals(chain, ProjectJson.decode(text).tracks.single().effects.chain)
    }

    @Test
    fun outOfRangeGainsAreClampedAndUnknownSlotFieldsKept() {
        val project = ProjectJson.decode("""
            {"version": 3, "id": 1, "name": "Neu", "createdAtMillis": 1, "sampleRate": 48000,
             "tracks": [{"id": 2, "name": "Spur", "fileName": "2.wav", "offsetFrames": 0, "lengthFrames": 48000,
                         "createdAtMillis": 2, "effects": {"chain": [
                           {"type": "myaudio.nam", "tone": "$hash", "name": "Amp", "inputDb": 99, "slim": 0.5}]}}]}
        """.trimIndent())
        val slot = project.tracks.single().effects.chain.single()
        assertEquals(ChainEffect.AmpModel(hash, "Amp", inputDb = ChainEffect.AmpModel.GAIN_RANGE_DB), slot.effect)
        assertTrue("\"slim\"" in ProjectJson.encode(project))
    }

    @Test
    fun masterChainKeepsCabinetSlots() {
        val text = ProjectJson.encode(Project(1, "Demo", 1, 48000, masterChain = listOf(
            EffectSlot(ChainEffect.Cabinet(hash, "Box"))
        )))
        assertTrue(ProjectJson.decode(text).masterChain.single().effect is ChainEffect.Cabinet)
    }
}
