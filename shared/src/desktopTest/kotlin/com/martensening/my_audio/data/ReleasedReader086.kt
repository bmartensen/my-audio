package com.martensening.my_audio.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The `project.json` reader as released in My Audio 0.8.5 and 0.8.6 (tags `v0.8.5`/`v0.8.6`), copied verbatim
 * apart from this comment and the object name. Installed apps read projects with exactly these rules, so tests
 * use it to prove that projects written by the current version still open there. Never update it to match
 * the current reader; add a reader for a newer released version instead.
 */
object ReleasedReader086 {
    private const val FORMAT_VERSION = 3
    private const val TONE = "myaudio.eq"
    private const val REVERB = "myaudio.reverb"
    private const val AIRWINDOWS = "airwindows."

    fun decode(text: String): Project = parse(JsonFields(Json.parseToJsonElement(text) as JsonObject))

    private fun parseMetronome(json: JsonFields?) = json?.let {
        Metronome(
            enabled = it.optBoolean("enabled", false),
            bpm = it.optInt("bpm", Metronome.DEFAULT_BPM),
            volume = it.optDouble("volume", Metronome.DEFAULT_VOLUME.toDouble()).toFloat(),
            duringPlayback = it.optBoolean("duringPlayback", false)
        )
    }

    private fun JsonFields?.float(key: String, default: Float) =
        this?.optDouble(key)?.takeUnless { it.isNaN() }?.toFloat() ?: default

    private fun parseEq(eq: JsonFields?) = EqEffect(
        enabled = eq?.optBoolean("enabled", false) ?: false,
        lowDb = eq.float("lowDb", 0f).coerceIn(-EqEffect.RANGE_DB, EqEffect.RANGE_DB),
        presenceDb = eq.float("presenceDb", 0f).coerceIn(-EqEffect.RANGE_DB, EqEffect.RANGE_DB),
        highDb = eq.float("highDb", 0f).coerceIn(-EqEffect.RANGE_DB, EqEffect.RANGE_DB)
    )

    private fun parseCompressor(compressor: JsonFields?) = CompressorEffect(
        enabled = compressor?.optBoolean("enabled", false) ?: false,
        amount = compressor.float("amount", CompressorEffect.DEFAULT_AMOUNT).coerceIn(0f, 1f)
    )

    private fun parseReverb(reverb: JsonFields?): ReverbEffect {
        val default = ReverbEffect()
        return ReverbEffect(
            seconds = reverb.float("seconds", default.seconds),
            preDelayMs = reverb.float("preDelayMs", default.preDelayMs),
            damping = reverb.float("damping", default.damping),
            mix = reverb.float("mix", default.mix)
        )
    }

    private fun parseChain(json: JsonArray?): List<EffectSlot> = json.orEmpty().map { element ->
        val slot = JsonFields(element as? JsonObject ?: throw IllegalArgumentException("Effect slot is not an object"))
        val effect = when (val type = slot.optString("type", "")) {
            TONE -> ChainEffect.Tone(parseEq(slot.optObject("eq")), parseCompressor(slot.optObject("compressor")))
            REVERB -> ChainEffect.Reverb(parseReverb(slot))
            else -> if (type.startsWith(AIRWINDOWS) && type.length > AIRWINDOWS.length) {
                ChainEffect.Airwindows(type.removePrefix(AIRWINDOWS), slot.optArray("values").orEmpty().map { value ->
                    ((value as? JsonPrimitive)?.content?.toFloatOrNull()?.takeUnless { it.isNaN() } ?: 0f).coerceIn(0f, 1f)
                }, levelMatch = slot.optBoolean("levelMatch", true))
            } else throw IllegalArgumentException("Unsupported effect $type")
        }
        EffectSlot(effect, slot.optBoolean("enabled", true))
    }.take(EffectSlot.MAX_PER_CHAIN)

    private fun legacyChain(json: JsonFields): List<EffectSlot> {
        val tone = ChainEffect.Tone(parseEq(json.optObject("eq")), parseCompressor(json.optObject("compressor")))
        val reverb = json.optObject("reverb")
        return listOfNotNull(
            EffectSlot(tone).takeIf { tone.audible },
            EffectSlot(ChainEffect.Reverb(parseReverb(reverb))).takeIf { reverb?.optBoolean("enabled", false) == true }
        )
    }

    private fun parseEffects(json: JsonFields?, version: Int): TrackEffects {
        if (json == null) return TrackEffects(removeMetronome = true, removeBleed = true)
        val normalize = json.optObject("normalize")
        return TrackEffects(
            removeMetronome = json.optBoolean("removeMetronome", true),
            removeBleed = json.optBoolean("removeBleed", true),
            normalize = NormalizeEffect(
                enabled = normalize?.optBoolean("enabled", false) ?: false,
                targetDb = normalize.float("targetDb", NormalizeEffect.DEFAULT_TARGET_DB)
            ),
            chain = if (version >= 3) parseChain(json.optArray("chain")) else legacyChain(json)
        )
    }

    private fun parse(json: JsonFields): Project {
        val version = json.optInt("version", 1)
        require(version in 1..FORMAT_VERSION) { "Unsupported project version" }
        val tracks = json.getArray("tracks")
        return Project(
            masterChain = if (version >= 3) parseChain(json.optObject("master")?.optArray("chain")) else emptyList(),
            id = json.getLong("id"),
            name = json.getString("name"),
            notes = json.optString("notes", ""),
            notesTextSize = json.optInt("notesTextSize", Project.DEFAULT_NOTES_TEXT_SIZE)
                .coerceIn(Project.MIN_NOTES_TEXT_SIZE, Project.MAX_NOTES_TEXT_SIZE),
            notesScroll = parseNotesScroll(json.optObject("notesScroll")),
            preRoll = parsePreRoll(json.optObject("preRoll")),
            createdAtMillis = json.getLong("createdAtMillis"),
            sampleRate = json.getInt("sampleRate"),
            nextTrackNumber = json.optInt("nextTrackNumber", tracks.size + 1),
            metronome = parseMetronome(json.optObject("metronome")) ?: Metronome(),
            loop = parseLoop(json.optObject("loop")),
            tracks = tracks.map { element ->
                val track = JsonFields(element as JsonObject)
                Track(
                    id = track.getLong("id"),
                    name = track.getString("name"),
                    fileName = track.getString("fileName"),
                    channels = track.optInt("channels", 1).also { require(it in 1..2 && (it == 1 || version >= 2)) },
                    offsetFrames = track.getLong("offsetFrames"),
                    lengthFrames = track.getLong("lengthFrames"),
                    volume = track.optDouble("volume", 1.0).toFloat(),
                    muted = track.optBoolean("muted", false),
                    solo = track.optBoolean("solo", false),
                    pan = track.optDouble("pan", 0.0).toFloat().takeUnless { it.isNaN() }?.coerceIn(-1f, 1f) ?: 0f,
                    createdAtMillis = track.getLong("createdAtMillis"),
                    recordedMetronome = parseMetronome(track.optObject("recordedMetronome")),
                    countInFrames = track.optLong("countInFrames", 0L),
                    referenceFileName = track.optString("referenceFileName", "").takeIf { it.isNotEmpty() },
                    effects = parseEffects(track.optObject("effects"), version),
                    volumeEnvelope = parseEnvelope(track.optArray("volumeEnvelope"))
                )
            }
        )
    }

    private fun parseNotesScroll(json: JsonFields?): NotesScroll {
        val seconds = json?.optDouble("secondsPerLine")?.takeUnless { it.isNaN() }?.toFloat()
            ?: NotesScroll.DEFAULT_SECONDS_PER_LINE
        return NotesScroll(
            enabled = json?.optBoolean("enabled", false) ?: false,
            secondsPerLine = seconds.coerceIn(NotesScroll.MIN_SECONDS_PER_LINE, NotesScroll.MAX_SECONDS_PER_LINE)
        )
    }

    private fun parsePreRoll(json: JsonFields?) = PreRoll(
        enabled = json?.optBoolean("enabled", false) ?: false,
        seconds = (json?.optInt("seconds", PreRoll.DEFAULT_SECONDS) ?: PreRoll.DEFAULT_SECONDS)
            .coerceIn(PreRoll.MIN_SECONDS, PreRoll.MAX_SECONDS)
    )

    private fun parseLoop(json: JsonFields?): PlaybackLoop? {
        if (json == null || !json.has("startFrames") || !json.has("endFrames")) return null
        val loop = PlaybackLoop(json.getLong("startFrames"), json.getLong("endFrames"), json.optBoolean("enabled", true))
        return loop.takeIf { it.startFrames >= 0 && it.lengthFrames > 0 }
    }

    private fun parseEnvelope(json: JsonArray?): List<VolumePoint> = VolumeEnvelope.sanitize(
        json.orEmpty().mapNotNull { element ->
            val point = (element as? JsonObject)?.let(::JsonFields) ?: return@mapNotNull null
            if (!point.has("frame") || !point.has("gain")) return@mapNotNull null
            VolumePoint(point.optLong("frame", -1), point.optDouble("gain").toFloat())
        }
    )
}
