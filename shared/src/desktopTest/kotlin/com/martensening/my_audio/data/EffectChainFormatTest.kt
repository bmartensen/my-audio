package com.martensening.my_audio.data

import com.martensening.my_audio.audio.AudioEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format version 3: effect chains per track and on the master, and the migration of versions 1 and 2. */
class EffectChainFormatTest {
    private fun legacyTrack(effects: String) = """
        {"version": 2, "id": 1, "name": "Alt", "createdAtMillis": 1, "sampleRate": 48000,
         "tracks": [{"id": 2, "name": "Spur", "fileName": "2.wav", "channels": 2, "offsetFrames": 0,
                     "lengthFrames": 48000, "createdAtMillis": 2, "effects": $effects}]}
    """.trimIndent()

    @Test
    fun legacyEffectsBecomeToneThenReverbInTheOldOrder() {
        val track = ProjectJson.decode(legacyTrack("""
            {"removeMetronome": false, "removeBleed": false,
             "eq": {"enabled": false, "lowDb": -3, "presenceDb": 3, "highDb": 2},
             "compressor": {"enabled": true, "amount": 0.5},
             "reverb": {"enabled": true, "seconds": 3.5, "preDelayMs": 35, "damping": 0.35, "mix": 0.3}}
        """)).tracks.single()
        assertEquals(
            listOf(
                // The switched-off EQ part keeps its values, exactly as before.
                EffectSlot(ChainEffect.Tone(EqEffect(false, -3f, 3f, 2f), CompressorEffect(true, 0.5f))),
                EffectSlot(ChainEffect.Reverb(ReverbEffect(3.5f, 35f, 0.35f, 0.3f)))
            ),
            track.effects.chain
        )
        assertEquals(2, track.channels)
    }

    @Test
    fun switchedOffLegacyEffectsLeaveNoSlot() {
        val track = ProjectJson.decode(legacyTrack("""
            {"eq": {"enabled": false, "lowDb": 4}, "compressor": {"enabled": false},
             "reverb": {"enabled": false, "seconds": 7}}
        """)).tracks.single()
        assertTrue(track.effects.chain.isEmpty())
    }

    @Test
    fun chainsRoundTripInTheirOrderWithSwitchesAndMasterInVersionThree() {
        val tone = ChainEffect.Tone().with(TonePreset.Guitar)
        val chain = listOf(
            EffectSlot(ChainEffect.Reverb(ReverbEffect().with(ReverbEffect.Preset.Room))),
            EffectSlot(tone, enabled = false),
            EffectSlot(tone.copy(compressor = tone.compressor.copy(enabled = false)))
        )
        val master = listOf(EffectSlot(ChainEffect.Tone().with(TonePreset.Neutral)), EffectSlot(ChainEffect.Reverb()))
        val project = Project(1, "Demo", 1, 48000, masterChain = master, tracks = listOf(
            Track(2, "Gitarre", "2.wav", 0, 48000, createdAtMillis = 2, effects = TrackEffects(chain = chain))
        ))

        val text = ProjectJson.encode(project)
        assertEquals(3, ProjectJson.formatVersion(text))
        val decoded = ProjectJson.decode(text)
        assertEquals(project, decoded)
        assertEquals(master, decoded.masterChain)
    }

    @Test
    fun projectsWithoutChainsKeepTheirOldVersion() {
        val mono = Project(1, "Demo", 1, 48000, tracks = listOf(Track(2, "A", "2.wav", 0, 1, createdAtMillis = 2)))
        assertEquals(1, ProjectJson.formatVersion(ProjectJson.encode(mono)))
        val stereo = mono.copy(tracks = mono.tracks.map { it.copy(channels = 2) })
        assertEquals(2, ProjectJson.formatVersion(ProjectJson.encode(stereo)))
        // A master chain alone already needs version 3.
        val mastered = mono.copy(masterChain = listOf(EffectSlot(ChainEffect.Reverb())))
        assertEquals(3, ProjectJson.formatVersion(ProjectJson.encode(mastered)))
    }

    @Test
    fun unknownEffectsAreKeptAndNewerVersionsRejected() {
        val unknown = """
            {"version": 3, "id": 1, "name": "Neu", "createdAtMillis": 1, "sampleRate": 48000,
             "tracks": [{"id": 2, "name": "Spur", "fileName": "2.wav", "offsetFrames": 0, "lengthFrames": 1,
                         "createdAtMillis": 2, "effects": {"chain": [{"type": "vst3.SomePlugin", "enabled": true}]}}]}
        """.trimIndent()
        // An effect of a newer version is kept, not dropped (see VersionExchangeTest).
        val slot = ProjectJson.decode(unknown).tracks.single().effects.chain.single()
        assertEquals("vst3.SomePlugin", (slot.effect as ChainEffect.Unknown).type)
        assertThrows(IllegalArgumentException::class.java) { ProjectJson.decode(unknown.replace("\"version\": 3", "\"version\": 4")) }
    }

    @Test
    fun airwindowsEffectsRoundTripByNameAndClampValues() {
        val chain = listOf(
            EffectSlot(ChainEffect.Airwindows("ToTape9", listOf(0.5f, 0f, 1f, 0.25f))),
            // A name this build may not know stays stored as it is.
            EffectSlot(ChainEffect.Airwindows("FromTheFuture", listOf(0.1f)), enabled = false)
        )
        val project = Project(1, "Demo", 1, 48000, tracks = listOf(
            Track(2, "Gitarre", "2.wav", 0, 48000, createdAtMillis = 2, effects = TrackEffects(chain = chain))
        ))
        val text = ProjectJson.encode(project)
        assertTrue("\"type\": \"airwindows.ToTape9\"" in text)
        assertEquals(project, ProjectJson.decode(text))
        val wild = text.replace("0.25", "7.5")
        assertEquals(1f, (ProjectJson.decode(wild).tracks.single().effects.chain[0].effect as ChainEffect.Airwindows).values[3])
    }

    @Test
    fun airwindowsLevelMatchDefaultsOnAndStoresOnlyTheException() {
        val matched = ChainEffect.Airwindows("Drive", listOf(0.85f, 0.65f, 1f, 1f))
        val project = Project(1, "Demo", 1, 48000, tracks = listOf(
            Track(2, "Gitarre", "2.wav", 0, 48000, createdAtMillis = 2, effects = TrackEffects(chain = listOf(
                EffectSlot(matched), EffectSlot(matched.copy(levelMatch = false))
            )))
        ))
        val text = ProjectJson.encode(project)
        assertEquals(1, Regex("\"levelMatch\"").findAll(text).count())
        assertEquals(project, ProjectJson.decode(text))
        // Slots saved by 0.8 to 0.8.2 have no field and are matched.
        val old = (ProjectJson.decode(text.replace(Regex(""",\s*"levelMatch": false"""), "")).tracks.single().effects.chain[1].effect
            as ChainEffect.Airwindows)
        assertTrue(old.levelMatch)
    }

    @Test
    fun librarySelectionStartsWithOwnEffectsAndPersists() {
        val folder = kotlin.io.path.createTempDirectory().toFile()
        try {
            val library = EffectLibrary(folder.resolve("library.json"))
            assertEquals(EffectLibrary.DEFAULT, library.selection.value)
            library.setSelected(EffectLibrary.airwindowsId("Galactic"), true)
            library.setSelected(EffectLibrary.REVERB, false)
            val reloaded = EffectLibrary(folder.resolve("library.json"))
            assertEquals(setOf(EffectLibrary.TONE, "airwindows.Galactic"), reloaded.selection.value)
            assertEquals("Galactic", EffectLibrary.airwindowsName("airwindows.Galactic"))
            assertEquals(null, EffectLibrary.airwindowsName(EffectLibrary.TONE))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun chainsAreLimitedToSixSlots() {
        val chain = List(8) { EffectSlot(ChainEffect.Reverb()) }
        val project = Project(1, "Demo", 1, 48000, masterChain = chain)
        assertEquals(EffectSlot.MAX_PER_CHAIN, ProjectJson.decode(ProjectJson.encode(project)).masterChain.size)
    }

    @Test
    fun nativeEncodingSkipsSwitchedOffSlotsAndParts() {
        val tone = ChainEffect.Tone(EqEffect(false, 1f, 2f, 3f), CompressorEffect(true, 0.25f))
        val encoded = AudioEngine.encodeChain(listOf(
            EffectSlot(ChainEffect.Reverb(ReverbEffect(2f, 10f, 0.5f, 0.3f)), enabled = false),
            EffectSlot(tone),
            EffectSlot(ChainEffect.Tone(EqEffect(false), CompressorEffect(false))),
            EffectSlot(ChainEffect.Reverb(ReverbEffect(2f, 10f, 0.5f, 0.3f)))
        ))
        assertArrayEquals(floatArrayOf(1f, 4f, Float.NaN, 2f, 3f, 0.25f, 2f, 4f, 2f, 10f, 0.5f, 0.3f), encoded, 0f)
    }
}
