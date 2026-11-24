package com.martensening.my_audio.data

import com.martensening.my_audio.audio.AudioEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exchange of projects between app versions in both directions: fields and effects of a newer version survive
 * reading and saving here, and projects written here still open in the released 0.8.5/0.8.6 reader.
 */
class VersionExchangeTest {
    private fun tree(text: String) = Json.parseToJsonElement(text).jsonObject

    /** A project using every object level the format has, as this version writes it. */
    private val full = Project(
        id = 1, name = "Demo", createdAtMillis = 1, sampleRate = 48000, nextTrackNumber = 3,
        metronome = Metronome(enabled = true, bpm = 96),
        loop = PlaybackLoop(48000, 96000),
        masterChain = listOf(EffectSlot(ChainEffect.Reverb())),
        tracks = listOf(
            Track(
                2, "Gitarre", "2.wav", -1020, 48000, createdAtMillis = 2,
                recordedMetronome = Metronome(enabled = true, bpm = 96),
                volumeEnvelope = listOf(VolumePoint(0, 1f), VolumePoint(24000, 0.5f)),
                effects = TrackEffects(
                    normalize = NormalizeEffect(enabled = true),
                    chain = listOf(
                        EffectSlot(ChainEffect.Tone().with(TonePreset.Guitar)),
                        EffectSlot(ChainEffect.Airwindows("ToTape9", listOf(0.5f)))
                    )
                )
            )
        ),
        recordTrack = RecordTrack(name = "Gesang", muted = false, effects = TrackEffects(chain = listOf(EffectSlot(ChainEffect.Reverb()))))
    )

    /** [json] with `"future": <path>` added inside every object at the given paths. */
    private fun withFutureFields(json: JsonObject, vararg paths: List<Any>): JsonObject {
        var result = json
        for (path in paths) result = addAt(result, path, JsonPrimitive(path.toString())) as JsonObject
        return result
    }

    private fun addAt(element: JsonElement, path: List<Any>, marker: JsonPrimitive): JsonElement = when {
        path.isEmpty() -> JsonObject(element.jsonObject + ("future" to marker))
        else -> when (val step = path.first()) {
            is String -> JsonObject(element.jsonObject + (step to addAt(element.jsonObject.getValue(step), path.drop(1), marker)))
            is Int -> kotlinx.serialization.json.JsonArray(element.jsonArray.mapIndexed { index, item ->
                if (index == step) addAt(item, path.drop(1), marker) else item
            })
            else -> error("Bad path $path")
        }
    }

    private val futurePaths = arrayOf(
        listOf(),
        listOf("notesScroll"), listOf("preRoll"), listOf("metronome"), listOf("loop"), listOf("master"),
        listOf("master", "chain", 0),
        listOf("tracks", 0), listOf("tracks", 0, "recordedMetronome"),
        listOf("tracks", 0, "effects"), listOf("tracks", 0, "effects", "normalize"),
        listOf("tracks", 0, "effects", "chain", 0), listOf("tracks", 0, "effects", "chain", 0, "eq"),
        listOf("tracks", 0, "effects", "chain", 0, "compressor"), listOf("tracks", 0, "effects", "chain", 1),
        listOf("recordTrack"), listOf("recordTrack", "effects"), listOf("recordTrack", "effects", "chain", 0)
    )

    @Test
    fun fieldsOfANewerVersionSurviveReadingAndSavingAtEveryLevel() {
        val newer = withFutureFields(tree(ProjectJson.encode(full)), *futurePaths)
        val project = ProjectJson.decode(newer.toString())
        assertEquals(full, project.copy(
            unknown = UnknownFields.NONE,
            masterChain = project.masterChain.map { it.copy(unknown = UnknownFields.NONE) },
            tracks = project.tracks.map { track ->
                track.copy(unknown = UnknownFields.NONE, effects = track.effects.copy(
                    unknown = UnknownFields.NONE, chain = track.effects.chain.map { it.copy(unknown = UnknownFields.NONE) }
                ))
            },
            recordTrack = project.recordTrack!!.copy(unknown = UnknownFields.NONE, effects = project.recordTrack!!.effects.copy(
                unknown = UnknownFields.NONE, chain = project.recordTrack!!.effects.chain.map { it.copy(unknown = UnknownFields.NONE) }
            ))
        ))
        // Saved again unchanged, the file is the newer one exactly.
        assertEquals(newer, tree(ProjectJson.encode(project)))
        // Edited here, the edit lands and every unknown field stays.
        val edited = tree(ProjectJson.encode(project.copy(
            name = "Neu",
            tracks = project.tracks.map { it.copy(volume = 0.5f, effects = it.effects.copy(normalize = NormalizeEffect())) }
        )))
        assertEquals(JsonPrimitive("Neu"), edited["name"])
        for (path in futurePaths) {
            var node: JsonElement = edited
            for (step in path) node = if (step is Int) node.jsonArray[step] else node.jsonObject.getValue(step as String)
            assertEquals("future field at $path", JsonPrimitive(path.toString()), node.jsonObject["future"])
        }
    }

    @Test
    fun unknownFieldsOfARemovedSubObjectGoWithIt() {
        val newer = withFutureFields(tree(ProjectJson.encode(full)), listOf("loop"), listOf("tracks", 0, "recordedMetronome"))
        val project = ProjectJson.decode(newer.toString())
        val saved = tree(ProjectJson.encode(project.copy(loop = null, tracks = project.tracks.map { it.copy(recordedMetronome = null) })))
        assertFalse("loop" in saved)
        assertFalse("recordedMetronome" in saved.getValue("tracks").jsonArray[0].jsonObject)
    }

    @Test
    fun masterFieldsOfANewerVersionStayWithoutAChain() {
        val newer = withFutureFields(tree(ProjectJson.encode(full)), listOf("master"))
        val project = ProjectJson.decode(newer.toString()).copy(masterChain = emptyList())
        val saved = tree(ProjectJson.encode(project))
        assertEquals(JsonPrimitive("[master]"), saved.getValue("master").jsonObject["future"])
        assertTrue(ProjectJson.decode(saved.toString()).masterChain.isEmpty())
    }

    @Test
    fun effectsOfANewerVersionOpenSilentlyAndAreKept() {
        val slot = buildJsonObject {
            put("type", "myaudio.chorus")
            put("enabled", false)
            put("depth", 0.4)
            put("voices", buildJsonObject { put("count", 3) })
        }
        val text = """
            {"version": 3, "id": 1, "name": "Neu", "createdAtMillis": 1, "sampleRate": 48000,
             "tracks": [{"id": 2, "name": "Spur", "fileName": "2.wav", "offsetFrames": 0, "lengthFrames": 1,
                         "createdAtMillis": 2, "effects": {"chain": [$slot, {"type": "myaudio.reverb"}]}}]}
        """.trimIndent()
        val chain = ProjectJson.decode(text).tracks.single().effects.chain
        val unknown = chain[0]
        assertEquals("myaudio.chorus", (unknown.effect as ChainEffect.Unknown).type)
        assertFalse(unknown.enabled)
        // Never rendered, even when switched on; the known reverb behind it still is.
        assertFalse(unknown.copy(enabled = true).audible)
        assertEquals(AudioEngine.encodeChain(chain.drop(1)).toList(), AudioEngine.encodeChain(chain.map { it.copy(enabled = true) }).toList())
        // Saving keeps it verbatim, with the bypass switch of this version.
        val project = ProjectJson.decode(text)
        val switchedOn = project.copy(tracks = project.tracks.map { track ->
            track.copy(effects = track.effects.copy(chain = track.effects.chain.mapIndexed { i, s -> if (i == 0) s.copy(enabled = true) else s }))
        })
        val saved = tree(ProjectJson.encode(switchedOn)).getValue("tracks").jsonArray[0].jsonObject
            .getValue("effects").jsonObject.getValue("chain").jsonArray[0].jsonObject
        assertEquals(JsonObject(slot + ("enabled" to JsonPrimitive(true))), saved)
    }

    @Test
    fun newerFormatVersionsAreStillRejected() {
        val text = ProjectJson.encode(full).replace("\"version\": 3", "\"version\": 4")
        assertThrows(IllegalArgumentException::class.java) { ProjectJson.decode(text) }
    }

    @Test
    fun projectsOfThisVersionOpenInTheReleasedReader() {
        val uncalibrated = full.copy(tracks = full.tracks.map { it.copy(latencyCalibrated = false) })
        val text = ProjectJson.encode(uncalibrated)
        assertEquals(3, ProjectJson.formatVersion(text))
        val old = ReleasedReader086.decode(text)
        // The released app sees every take with its settings; record track and calibration mark are invisible to it.
        assertEquals(full.copy(recordTrack = null), old)
        assertEquals(uncalibrated, ProjectJson.decode(text))
    }

    @Test
    fun newFieldsAloneDoNotRaiseTheVersion() {
        val simple = Project(
            1, "Demo", 1, 48000,
            tracks = listOf(Track(2, "A", "2.wav", 0, 48000, createdAtMillis = 2, latencyCalibrated = false)),
            recordTrack = RecordTrack(name = "B", muted = false, solo = true)
        )
        val text = ProjectJson.encode(simple)
        assertEquals(1, ProjectJson.formatVersion(text))
        assertEquals(simple, ProjectJson.decode(text))
        assertEquals(simple.copy(recordTrack = null, tracks = simple.tracks.map { it.copy(latencyCalibrated = true) }), ReleasedReader086.decode(text))
        // A chain on the record track needs version 3, so this reader parses it as a chain.
        val withChain = simple.copy(recordTrack = simple.recordTrack!!.copy(effects = TrackEffects(chain = listOf(EffectSlot(ChainEffect.Reverb())))))
        assertEquals(3, ProjectJson.formatVersion(ProjectJson.encode(withChain)))
        assertEquals(withChain, ProjectJson.decode(ProjectJson.encode(withChain)))
    }

    @Test
    fun latencyAndOffsetKeepTheVersionAndOlderReadersPlayTheShiftedPosition() {
        // A take from the start marker 96 000, moved 4 800 frames later by the user.
        val take = Track(2, "A", "2.wav", 96_000 - 1_020, 48000, createdAtMillis = 2, latencyFrames = 1_020)
            .withTiming(shift = 4_800)
        val simple = Project(1, "Demo", 1, 48000, tracks = listOf(take))
        val text = ProjectJson.encode(simple)
        assertEquals(1, ProjectJson.formatVersion(text))
        assertEquals(simple, ProjectJson.decode(text))
        // offsetFrames stays the effective position, so an older reader places the track exactly there.
        val old = ReleasedReader086.decode(text).tracks.single()
        assertEquals(96_000L - 1_020L + 4_800L, old.offsetFrames)
        assertEquals(take.offsetFrames, old.offsetFrames)
    }

    @Test
    fun aRecordTrackWithoutFieldsIsAFreshOne() {
        val text = """
            {"version": 1, "id": 1, "name": "Neu", "createdAtMillis": 1, "sampleRate": 48000, "tracks": [],
             "recordTrack": {}}
        """.trimIndent()
        assertEquals(RecordTrack(), ProjectJson.decode(text).recordTrack)
        assertTrue(ProjectJson.decode(text).recordTrack!!.isPristine)
    }
}
