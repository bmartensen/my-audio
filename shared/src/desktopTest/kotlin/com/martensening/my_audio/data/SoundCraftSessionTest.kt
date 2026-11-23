package com.martensening.my_audio.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The SoundCraft session JSON (format version 1) and the hybrid effect split, without the native engine. */
class SoundCraftSessionTest {
    private val eq = EffectSlot(ChainEffect.Tone().with(TonePreset.Guitar))
    private val reverb = EffectSlot(ChainEffect.Reverb())
    private val airwindows = EffectSlot(ChainEffect.Airwindows("Density", listOf(0.5f)))

    @Test
    fun theChainIsRenderedUpToTheLastSlotWithoutEquivalent() {
        val (render, inserts) = SoundCraftSession.split(listOf(eq, airwindows, reverb, EffectSlot(ChainEffect.Reverb(), enabled = false)))
        assertEquals(listOf(eq, airwindows), render)
        assertEquals(listOf(reverb), inserts)
        assertEquals(emptyList<EffectSlot>() to listOf(eq, reverb), SoundCraftSession.split(listOf(eq, reverb)))
    }

    @Test
    fun tracksKeepPositionLevelsPlaylistsAndMappedInserts() {
        val track = Track(1, "Gitarre", "1.wav", -900, 96000, volume = 0.5f, muted = true, pan = -0.5f, createdAtMillis = 1,
            effects = TrackEffects(chain = listOf(eq, airwindows, reverb)),
            volumeEnvelope = listOf(VolumePoint(0, 1f), VolumePoint(48000, 0f)))
        val project = Project(1, "Demo", 1, 48000, tracks = listOf(track), notes = "Am F C G",
            metronome = Metronome(bpm = 96))
        val exported = SoundCraftSession.ExportedTrack(track, start = 0, channels = 1, dryFile = "01 Gitarre (Dry).wav",
            dryFrames = 95100, wetFile = "01 Gitarre.wav", wetFrames = 140000, inserts = listOf(reverb))
        val session = Json.parseToJsonElement(SoundCraftSession.json(project, listOf(exported), masterGainDb = 4.0)).jsonObject

        assertEquals(1, session["format_version"]!!.jsonPrimitive.int)
        assertEquals("Am F C G", session["comments"]!!.jsonPrimitive.content)
        assertEquals(96.0, session["tempo"]!!.jsonObject["tempos"]!!.jsonArray[0].jsonObject["bpm"]!!.jsonPrimitive.double, 0.0)
        val tracks = session["tracks"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Audio", "Master"), tracks.map { it["kind"]!!.jsonPrimitive.content })

        val audio = tracks[0]
        val playlists = audio["playlists"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Gitarre", "Dry"), playlists.map { it["name"]!!.jsonPrimitive.content })
        val clip = playlists[0]["clips"]!!.jsonArray[0].jsonObject
        assertEquals(0L, clip["start"]!!.jsonPrimitive.long)
        assertEquals(140000L, clip["length"]!!.jsonPrimitive.long)
        val mixer = audio["mixer"]!!.jsonObject
        assertTrue(mixer["mute"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(-0.5, mixer["pan"]!!.jsonArray[0].jsonPrimitive.double, 1e-6)
        val inserts = mixer["inserts"]!!.jsonArray
        assertEquals(10, inserts.size)
        assertEquals("room_reverb", inserts[0].jsonObject["plugin"]!!.jsonPrimitive.content)
        assertTrue(inserts.drop(1).all { it is JsonNull })
        // -6.02 dB of the fader, +3.01 dB for SoundCraft's centre pan law, plus the reverb's dry-level make-up.
        val angle = ReverbEffect().mix * Math.PI / 2
        val wet = Math.tan(angle) / (1 + Math.tan(angle))
        val expected = 20 * Math.log10(0.5) + 3.0103 + 20 * Math.log10(Math.cos(angle) / (1 - wet))
        assertEquals(expected, mixer["volume_db"]!!.jsonPrimitive.double, 1e-4)
        val points = audio["automation"]!!.jsonArray[0].jsonObject["points"] as JsonArray
        assertEquals(-144.0, points[1].jsonObject["value"]!!.jsonPrimitive.double, 1e-6)

        // Every id is unique and below next_id.
        val ids = tracks.map { it["id"]!!.jsonPrimitive.long } + session["sources"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.long } +
            playlists.flatMap { it["clips"]!!.jsonArray.map { c -> c.jsonObject["id"]!!.jsonPrimitive.long } }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it < session["next_id"]!!.jsonPrimitive.long })

        val master = tracks[1]["mixer"]!!.jsonObject
        val maximizer = master["inserts"]!!.jsonArray[0] as JsonObject
        assertEquals("maximizer", maximizer["plugin"]!!.jsonPrimitive.content)
        assertEquals(-4.0, maximizer["params"]!!.jsonObject["threshold"]!!.jsonPrimitive.double, 1e-6)
    }
}
