package com.martensening.my_audio.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `project.json`, versions 1 (mono), 2 (stereo) and 3 (effect chains); see the wiki page "Data format". Reading is
 * as lenient as the former org.json code: missing optional fields take their defaults, numbers may come as strings,
 * and a `null` value counts as missing. Writing leaves out fields whose value is null. Versions 1 and 2 stored EQ,
 * compressor and reverb as fixed fields; they are read as an equivalent chain.
 *
 * Exchange between app versions: new features are optional fields that older versions ignore, so the version only
 * rises for a change of meaning. Fields this version does not know are kept in [UnknownFields] and written back,
 * and an effect type it does not know stays as a silent [ChainEffect.Unknown] slot.
 */
object ProjectJson {
    const val FORMAT_VERSION = 3
    private const val TONE = "myaudio.eq"
    private const val REVERB = "myaudio.reverb"
    private const val AIRWINDOWS = "airwindows."

    // Keys each object level understands; everything else is preserved as unknown.
    private val METRONOME_KEYS = setOf("enabled", "bpm", "volume", "duringPlayback")
    private val PROJECT_KEYS = setOf(
        "version", "id", "name", "notes", "notesTextSize", "notesScroll", "preRoll", "createdAtMillis", "sampleRate",
        "nextTrackNumber", "metronome", "master", "loop", "tracks", "recordTrack"
    )
    private val PROJECT_NESTED = mapOf(
        "notesScroll" to setOf("enabled", "secondsPerLine"),
        "preRoll" to setOf("enabled", "seconds"),
        "metronome" to METRONOME_KEYS,
        "loop" to setOf("startFrames", "endFrames", "enabled"),
        "master" to setOf("chain")
    )
    private val TRACK_KEYS = setOf(
        "id", "name", "fileName", "channels", "offsetFrames", "lengthFrames", "volume", "muted", "solo", "pan",
        "createdAtMillis", "recordedMetronome", "countInFrames", "referenceFileName", "effects", "volumeEnvelope",
        "latencyCalibrated"
    )
    private val TRACK_NESTED = mapOf("recordedMetronome" to METRONOME_KEYS)
    private val RECORD_TRACK_KEYS = setOf("name", "volume", "muted", "solo", "pan", "effects", "volumeEnvelope")
    // "eq", "compressor" and "reverb" are the fixed effects of versions 1 and 2; they migrate into the chain.
    private val EFFECTS_KEYS = setOf("removeMetronome", "removeBleed", "normalize", "chain", "eq", "compressor", "reverb")
    private val EFFECTS_NESTED = mapOf("normalize" to setOf("enabled", "targetDb"))
    private val TONE_KEYS = setOf("type", "enabled", "eq", "compressor")
    private val TONE_NESTED = mapOf(
        "eq" to setOf("enabled", "lowDb", "presenceDb", "highDb"),
        "compressor" to setOf("enabled", "amount")
    )
    private val REVERB_KEYS = setOf("type", "enabled", "seconds", "preDelayMs", "damping", "mix")
    private val AIRWINDOWS_KEYS = setOf("type", "enabled", "values", "levelMatch")

    @OptIn(ExperimentalSerializationApi::class)
    private val format = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun encode(project: Project): String = format.encodeToString(JsonElement.serializer(), toJson(project))

    fun decode(text: String): Project = parse(JsonFields(Json.parseToJsonElement(text) as JsonObject))

    /** Format version a file claims; files from before the field existed are version 1. */
    fun formatVersion(text: String): Int = JsonFields(Json.parseToJsonElement(text) as JsonObject).optInt("version", 1)

    /** The lowest version that holds everything in [project], so older apps keep opening simple projects. */
    private fun version(project: Project) = when {
        project.masterChain.isNotEmpty() || project.tracks.any { it.effects.chain.isNotEmpty() } ||
            project.recordTrack?.effects?.chain?.isNotEmpty() == true -> 3
        project.tracks.any { it.channels == 2 } -> 2
        else -> 1
    }

    /** Adds the preserved [unknown] fields after the known ones; known fields always win. */
    private fun JsonObject.with(unknown: UnknownFields): JsonObject {
        if (unknown.isEmpty) return this
        val merged = LinkedHashMap<String, JsonElement>(this)
        for ((key, fields) in unknown.nested) {
            // A sub-object that is no longer written (e.g. a removed loop) takes its unknown fields with it.
            val known = merged[key] as? JsonObject ?: continue
            merged[key] = JsonObject(known + fields.filterKeys { it !in known })
        }
        for ((key, value) in unknown.own) if (key !in merged) merged[key] = value
        return JsonObject(merged)
    }

    private fun toJson(project: Project) = buildJsonObject {
        put("version", version(project))
        put("id", project.id)
        put("name", project.name)
        put("notes", project.notes)
        put("notesTextSize", project.notesTextSize)
        put("notesScroll", buildJsonObject {
            put("enabled", project.notesScroll.enabled)
            put("secondsPerLine", project.notesScroll.secondsPerLine.toDouble())
        })
        put("preRoll", buildJsonObject {
            put("enabled", project.preRoll.enabled)
            put("seconds", project.preRoll.seconds)
        })
        put("createdAtMillis", project.createdAtMillis)
        put("sampleRate", project.sampleRate)
        put("nextTrackNumber", project.nextTrackNumber)
        put("metronome", metronomeJson(project.metronome))
        // Also written without a chain when a newer version stored other master fields.
        if (project.masterChain.isNotEmpty() || "master" in project.unknown.nested) {
            put("master", buildJsonObject { if (project.masterChain.isNotEmpty()) put("chain", chainJson(project.masterChain)) })
        }
        project.loop?.let { loop ->
            put("loop", buildJsonObject {
                put("startFrames", loop.startFrames)
                put("endFrames", loop.endFrames)
                put("enabled", loop.enabled)
            })
        }
        put("tracks", buildJsonArray { for (track in project.tracks) add(trackJson(track)) })
        project.recordTrack?.let { put("recordTrack", recordTrackJson(it)) }
    }.with(project.unknown)

    private fun trackJson(track: Track) = buildJsonObject {
        put("id", track.id)
        put("name", track.name)
        put("fileName", track.fileName)
        if (track.channels != 1) put("channels", track.channels)
        put("offsetFrames", track.offsetFrames)
        put("lengthFrames", track.lengthFrames)
        put("volume", track.volume.toDouble())
        put("muted", track.muted)
        put("solo", track.solo)
        put("pan", track.pan.toDouble())
        put("createdAtMillis", track.createdAtMillis)
        track.recordedMetronome?.let { put("recordedMetronome", metronomeJson(it)) }
        put("countInFrames", track.countInFrames)
        track.referenceFileName?.let { put("referenceFileName", it) }
        // Missing means calibrated; only the exception is written.
        if (!track.latencyCalibrated) put("latencyCalibrated", false)
        put("effects", effectsJson(track.effects))
        put("volumeEnvelope", envelopeJson(track.volumeEnvelope))
    }.with(track.unknown)

    private fun recordTrackJson(record: RecordTrack) = buildJsonObject {
        record.name?.let { put("name", it) }
        put("volume", record.volume.toDouble())
        put("muted", record.muted)
        put("solo", record.solo)
        put("pan", record.pan.toDouble())
        put("effects", effectsJson(record.effects))
        put("volumeEnvelope", envelopeJson(record.volumeEnvelope))
    }.with(record.unknown)

    private fun envelopeJson(points: List<VolumePoint>) = buildJsonArray {
        for (point in points) add(buildJsonObject {
            put("frame", point.frame)
            put("gain", point.gain.toDouble())
        })
    }

    private fun metronomeJson(metronome: Metronome) = buildJsonObject {
        put("enabled", metronome.enabled)
        put("bpm", metronome.bpm)
        put("volume", metronome.volume.toDouble())
        put("duringPlayback", metronome.duringPlayback)
    }

    private fun parseMetronome(json: JsonFields?) = json?.let {
        Metronome(
            enabled = it.optBoolean("enabled", false),
            bpm = it.optInt("bpm", Metronome.DEFAULT_BPM),
            volume = it.optDouble("volume", Metronome.DEFAULT_VOLUME.toDouble()).toFloat(),
            duringPlayback = it.optBoolean("duringPlayback", false)
        )
    }

    private fun effectsJson(effects: TrackEffects) = buildJsonObject {
        put("removeMetronome", effects.removeMetronome)
        put("removeBleed", effects.removeBleed)
        put("normalize", buildJsonObject {
            put("enabled", effects.normalize.enabled)
            put("targetDb", effects.normalize.targetDb.toDouble())
        })
        if (effects.chain.isNotEmpty()) put("chain", chainJson(effects.chain))
    }.with(effects.unknown)

    private fun chainJson(chain: List<EffectSlot>) = buildJsonArray {
        for (slot in chain) add(slotJson(slot))
    }

    private fun slotJson(slot: EffectSlot): JsonObject = buildJsonObject {
        put("enabled", slot.enabled)
        when (val effect = slot.effect) {
            is ChainEffect.Tone -> {
                put("type", TONE)
                put("eq", buildJsonObject {
                    put("enabled", effect.eq.enabled)
                    put("lowDb", effect.eq.lowDb.toDouble())
                    put("presenceDb", effect.eq.presenceDb.toDouble())
                    put("highDb", effect.eq.highDb.toDouble())
                })
                put("compressor", buildJsonObject {
                    put("enabled", effect.compressor.enabled)
                    put("amount", effect.compressor.amount.toDouble())
                })
            }
            is ChainEffect.Reverb -> {
                put("type", REVERB)
                put("seconds", effect.reverb.seconds.toDouble())
                put("preDelayMs", effect.reverb.preDelayMs.toDouble())
                put("damping", effect.reverb.damping.toDouble())
                put("mix", effect.reverb.mix.toDouble())
            }
            is ChainEffect.Airwindows -> {
                put("type", AIRWINDOWS + effect.name)
                put("values", buildJsonArray { effect.values.forEach { add(JsonPrimitive(it.toDouble())) } })
                // Missing means on; only the exception is written.
                if (!effect.levelMatch) put("levelMatch", false)
            }
            // Written back exactly as read; only the bypass switch belongs to this version.
            is ChainEffect.Unknown -> for ((key, value) in effect.fields) if (key != "enabled") put(key, value)
        }
    }.with(slot.unknown)

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

    /**
     * An effect type this version does not know comes from a newer app. Dropping it would silently change the
     * sound after saving, so it stays as a [ChainEffect.Unknown] slot that is shown but not rendered. Airwindows
     * effects are likewise kept by name even if this build lacks one.
     */
    private fun parseChain(json: JsonArray?): List<EffectSlot> = json.orEmpty().map { element ->
        val slot = JsonFields(element as? JsonObject ?: throw IllegalArgumentException("Effect slot is not an object"))
        val enabled = slot.optBoolean("enabled", true)
        when (val type = slot.optString("type", "")) {
            TONE -> EffectSlot(
                ChainEffect.Tone(parseEq(slot.optObject("eq")), parseCompressor(slot.optObject("compressor"))),
                enabled,
                slot.unknown(TONE_KEYS, TONE_NESTED)
            )
            REVERB -> EffectSlot(ChainEffect.Reverb(parseReverb(slot)), enabled, slot.unknown(REVERB_KEYS))
            else -> if (type.startsWith(AIRWINDOWS) && type.length > AIRWINDOWS.length) {
                EffectSlot(
                    ChainEffect.Airwindows(type.removePrefix(AIRWINDOWS), slot.optArray("values").orEmpty().map { value ->
                        ((value as? JsonPrimitive)?.content?.toFloatOrNull()?.takeUnless { it.isNaN() } ?: 0f).coerceIn(0f, 1f)
                    }, levelMatch = slot.optBoolean("levelMatch", true)),
                    enabled,
                    slot.unknown(AIRWINDOWS_KEYS)
                )
            } else {
                EffectSlot(ChainEffect.Unknown(type, JsonObject(slot.raw.filterKeys { it != "enabled" })), enabled)
            }
        }
    }.take(EffectSlot.MAX_PER_CHAIN)

    /**
     * Versions 1 and 2 applied EQ, compressor and reverb in this fixed order. Switched-on effects become the
     * equivalent chain „EQ (My Audio)“ → „Hall (My Audio)“, which renders sample-identically; switched-off ones
     * had no audible effect and are dropped.
     */
    private fun legacyChain(json: JsonFields): List<EffectSlot> {
        val tone = ChainEffect.Tone(parseEq(json.optObject("eq")), parseCompressor(json.optObject("compressor")))
        val reverb = json.optObject("reverb")
        return listOfNotNull(
            EffectSlot(tone).takeIf { tone.audible },
            EffectSlot(ChainEffect.Reverb(parseReverb(reverb))).takeIf { reverb?.optBoolean("enabled", false) == true }
        )
    }

    private fun parseEffects(json: JsonFields?, version: Int): TrackEffects {
        // Legacy files without effect fields used automatic cleanup. Only new takes default off.
        if (json == null) return TrackEffects(removeMetronome = true, removeBleed = true)
        val normalize = json.optObject("normalize")
        return TrackEffects(
            removeMetronome = json.optBoolean("removeMetronome", true),
            removeBleed = json.optBoolean("removeBleed", true),
            normalize = NormalizeEffect(
                enabled = normalize?.optBoolean("enabled", false) ?: false,
                targetDb = normalize.float("targetDb", NormalizeEffect.DEFAULT_TARGET_DB)
            ),
            chain = if (version >= 3) parseChain(json.optArray("chain")) else legacyChain(json),
            unknown = json.unknown(EFFECTS_KEYS, EFFECTS_NESTED)
        )
    }

    private fun parsePan(json: JsonFields) =
        json.optDouble("pan", 0.0).toFloat().takeUnless { it.isNaN() }?.coerceIn(-1f, 1f) ?: 0f

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
            tracks = tracks.map { element -> parseTrack(JsonFields(element as JsonObject), version) },
            recordTrack = json.optObject("recordTrack")?.let { parseRecordTrack(it, version) },
            unknown = json.unknown(PROJECT_KEYS, PROJECT_NESTED)
        )
    }

    private fun parseTrack(track: JsonFields, version: Int) = Track(
        id = track.getLong("id"),
        name = track.getString("name"),
        fileName = track.getString("fileName"),
        channels = track.optInt("channels", 1).also { require(it in 1..2 && (it == 1 || version >= 2)) },
        offsetFrames = track.getLong("offsetFrames"),
        lengthFrames = track.getLong("lengthFrames"),
        volume = track.optDouble("volume", 1.0).toFloat(),
        muted = track.optBoolean("muted", false),
        solo = track.optBoolean("solo", false),
        pan = parsePan(track),
        createdAtMillis = track.getLong("createdAtMillis"),
        recordedMetronome = parseMetronome(track.optObject("recordedMetronome")),
        countInFrames = track.optLong("countInFrames", 0L),
        referenceFileName = track.optString("referenceFileName", "").takeIf { it.isNotEmpty() },
        effects = parseEffects(track.optObject("effects"), version),
        volumeEnvelope = parseEnvelope(track.optArray("volumeEnvelope")),
        latencyCalibrated = track.optBoolean("latencyCalibrated", true),
        unknown = track.unknown(TRACK_KEYS, TRACK_NESTED)
    )

    private fun parseRecordTrack(json: JsonFields, version: Int) = RecordTrack(
        name = json.optString("name", "").takeIf { it.isNotEmpty() },
        volume = json.optDouble("volume", 1.0).toFloat(),
        muted = json.optBoolean("muted", true),
        solo = json.optBoolean("solo", false),
        pan = parsePan(json),
        // Written by 0.9 or later, so missing effects mean the defaults of a new take, not legacy cleanup.
        effects = json.optObject("effects")?.let { parseEffects(it, version) } ?: TrackEffects(),
        volumeEnvelope = parseEnvelope(json.optArray("volumeEnvelope")),
        unknown = json.unknown(RECORD_TRACK_KEYS)
    )

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

/** org.json-style access: `opt*` falls back to a default, `get*` throws when a required field is unusable. */
internal class JsonFields(val raw: JsonObject) {
    private fun primitive(key: String): JsonPrimitive? = (raw[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    fun has(key: String) = raw[key].let { it != null && it !is JsonNull }

    fun optBoolean(key: String, default: Boolean): Boolean = primitive(key)?.let { value ->
        value.booleanOrNull ?: when (value.content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    } ?: default

    fun optDouble(key: String, default: Double = Double.NaN): Double = primitive(key)?.content?.toDoubleOrNull() ?: default

    fun optLong(key: String, default: Long): Long = primitive(key)?.content?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: default

    fun optInt(key: String, default: Int): Int = primitive(key)?.content?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() } ?: default

    fun optString(key: String, default: String): String = primitive(key)?.content ?: default

    fun optObject(key: String): JsonFields? = (raw[key] as? JsonObject)?.let(::JsonFields)

    fun optArray(key: String): JsonArray? = raw[key] as? JsonArray

    fun getArray(key: String): JsonArray = optArray(key) ?: throw IllegalArgumentException("$key is not an array")

    fun getLong(key: String): Long = primitive(key)?.content?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
        ?: throw IllegalArgumentException("$key is not a number")

    fun getInt(key: String): Int = getLong(key).toInt()

    fun getString(key: String): String = primitive(key)?.takeIf { it.isString }?.content
        ?: throw IllegalArgumentException("$key is not a string")

    /** The fields outside [known], and those outside the known keys of each sub-object in [nested]. */
    fun unknown(known: Set<String>, nested: Map<String, Set<String>> = emptyMap()): UnknownFields {
        val own = raw.filterKeys { it !in known }
        val inner = nested.mapNotNull { (key, keys) ->
            val fields = (raw[key] as? JsonObject)?.filterKeys { it !in keys }
            if (fields.isNullOrEmpty()) null else key to JsonObject(fields)
        }.toMap()
        return if (own.isEmpty() && inner.isEmpty()) UnknownFields.NONE else UnknownFields(JsonObject(own), inner)
    }
}
