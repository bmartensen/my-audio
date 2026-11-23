package com.martensening.my_audio.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A project as a SoundCraft session (`.scraft`, format version 1 of `craft-libs/scraft-format`), written as
 * SoundCraft itself writes a new session; all times are sample frames at 48 kHz.
 *
 * Effects are hybrid: „EQ (My Audio)“ and „Hall (My Audio)“ become approximate built-in SoundCraft inserts;
 * Airwindows, amp models and cabinets have no equivalent, so a track's chain up to and including the last such
 * slot is rendered into its audio ([split]) and the untouched take stays available as playlist „Dry“.
 */
object SoundCraftSession {
    const val FORMAT_VERSION = 1
    const val AUDIO_DIR = "Audio Files"
    private const val SLOTS = 10
    private const val SILENT_DB = -144.0
    /**
     * Both pan with constant power, but SoundCraft's default law sits 3.01 dB lower in the centre than My Audio's
     * (whose centred mono track keeps unity gain on each side), so mono tracks are raised by that much.
     */
    private const val MONO_PAN_COMPENSATION_DB = 3.0103

    /** One exported track: its audio files and where its clip starts. */
    data class ExportedTrack(
        val track: Track,
        val start: Long,
        val channels: Int,
        val dryFile: String,
        val dryFrames: Long,
        /** The take through the rendered part of its chain; null when nothing had to be rendered. */
        val wetFile: String?,
        val wetFrames: Long,
        val inserts: List<EffectSlot>
    )

    /** Whether SoundCraft has a built-in equivalent, so the slot can stay an editable insert. */
    fun mappable(effect: ChainEffect) = effect is ChainEffect.Tone || effect is ChainEffect.Reverb

    /** The audible slots split into the part rendered into the audio and the part that stays inserts. */
    fun split(chain: List<EffectSlot>): Pair<List<EffectSlot>, List<EffectSlot>> {
        val audible = chain.filter { it.audible }
        val last = audible.indexOfLast { !mappable(it.effect) }
        return audible.subList(0, last + 1) to audible.subList(last + 1, audible.size)
    }

    /** Session JSON for [project]; [masterGainDb] is the gain My Audio's fixed master stage applies to the mix. */
    fun json(project: Project, tracks: List<ExportedTrack>, masterGainDb: Double): String {
        var nextId = 1L
        fun id() = nextId++
        val trackJson = mutableListOf<JsonObject>()
        val sources = mutableListOf<JsonObject>()
        val skipped = mutableListOf<String>()
        tracks.forEachIndexed { index, exported ->
            val track = exported.track
            val trackId = id()
            fun clip(file: String, frames: Long): JsonObject {
                val sourceId = id()
                val name = file.removeSuffix(".wav")
                sources += source(sourceId, name, file, exported.channels, frames)
                return clip(id(), name, exported.start, frames, sourceId)
            }
            val main = clip(exported.wetFile ?: exported.dryFile, if (exported.wetFile != null) exported.wetFrames else exported.dryFrames)
            val playlists = buildJsonArray {
                add(playlist(track.name, listOf(main)))
                if (exported.wetFile != null) add(playlist("Dry", listOf(clip(exported.dryFile, exported.dryFrames))))
            }
            val panCompensation = if (exported.channels == 1) MONO_PAN_COMPENSATION_DB else 0.0
            val (trackInserts, insertCompensation) = inserts(exported.inserts)
            val compensation = panCompensation + insertCompensation
            val volumeDb = compensated(track.volume, compensation)
            val automation = buildJsonArray {
                if (track.volumeEnvelope.isNotEmpty()) add(buildJsonObject {
                    put("param", "Volume")
                    putJsonArray("points") {
                        for (point in track.volumeEnvelope) add(buildJsonObject {
                            put("at", point.frame)
                            put("value", compensated(track.volume * point.gain, compensation))
                        })
                    }
                    put("enabled", true)
                })
            }
            trackJson += track(
                trackId, track.name, "Audio", if (exported.channels == 2) "Stereo" else "Mono", COLORS[index % COLORS.size],
                playlists, mixer(volumeDb, pans(track.pan, exported.channels), track.muted, track.solo, trackInserts),
                automation
            )
        }
        // The master chain has no audio of its own to render into, so only mappable slots carry over.
        val masterSlots = project.masterChain.filter { it.audible }
        masterSlots.filterNot { mappable(it.effect) }.forEach { skipped += it.effect.label() }
        val maximizer = buildJsonObject {
            put("plugin", "maximizer")
            putJsonObject("params") {
                put("ceiling", -1.0)
                put("release", 50.0)
                put("threshold", -max(0.0, masterGainDb).coerceAtMost(30.0))
            }
            put("bypass", false)
            put("active", true)
            put("preset", "")
        }
        val (masterInserts, masterCompensation) = inserts(masterSlots.filter { mappable(it.effect) })
        trackJson += track(
            id(), "Master", "Master", "Stereo", intArrayOf(120, 120, 120), buildJsonArray { add(playlist("Master", emptyList())) },
            mixer((masterGainDb.coerceAtMost(0.0) + masterCompensation).coerceAtMost(12.0), listOf(-1.0, 1.0), mute = false,
                solo = false, inserts = masterInserts + maximizer),
            JsonArray(emptyList())
        )
        val comments = listOfNotNull(
            project.notes.takeIf { it.isNotBlank() },
            skipped.distinct().takeIf { it.isNotEmpty() }?.let { "Not exported (master): " + it.joinToString(", ") }
        ).joinToString("\n\n")
        val session = buildJsonObject {
            put("format_version", FORMAT_VERSION)
            put("name", project.name)
            put("sample_rate", 48000)
            put("bit_depth", "Int24")
            put("frame_rate", "Fps30")
            put("timecode_start", 0)
            putJsonObject("tempo") {
                putJsonArray("tempos") { add(buildJsonObject { put("tick", 0); put("bpm", project.metronome.bpm.toDouble()) }) }
                putJsonArray("meters") {
                    add(buildJsonObject { put("tick", 0); put("numerator", 4); put("denominator", 4) })
                }
            }
            put("key_signatures", JsonArray(emptyList()))
            put("tracks", JsonArray(trackJson))
            put("sources", JsonArray(sources))
            put("videos", JsonArray(emptyList()))
            put("busses", JsonArray(emptyList()))
            putJsonArray("outputs") {
                add(buildJsonObject { put("name", "Out 1-2"); put("first_channel", 0); put("format", "Stereo") })
            }
            put("markers", JsonArray(emptyList()))
            put("groups", JsonArray(emptyList()))
            put("auto_names", JsonObject(emptyMap()))
            put("edit", EDIT)
            put("next_id", nextId)
            put("comments", comments)
            put("midi_originals", JsonObject(emptyMap()))
        }
        return PRETTY.encodeToString(JsonElement.serializer(), session)
    }

    /**
     * Built-in SoundCraft inserts approximating the mappable slots, in order, and the level (dB) the channel must
     * add so the result is as loud as in My Audio.
     */
    private fun inserts(slots: List<EffectSlot>): Pair<List<JsonObject>, Double> {
        var compensationDb = 0.0
        val inserts = slots.flatMap { slot -> insertsOf(slot) { compensationDb += it } }
        return inserts to compensationDb
    }

    private fun insertsOf(slot: EffectSlot, compensate: (Double) -> Unit): List<JsonObject> =
        when (val effect = slot.effect) {
            is ChainEffect.Tone -> listOfNotNull(
                // Low shelf 150 Hz, presence peak 3 kHz (Q 1) and high shelf 8 kHz, as in Tone.h.
                effect.eq.takeIf { it.enabled }?.let { eq ->
                    insert("eq_7band", mapOf(
                        "low_shelf_freq" to 150.0, "low_shelf_gain" to eq.lowDb.toDouble(), "low_shelf_q" to 0.707,
                        "mid_freq" to 3000.0, "mid_gain" to eq.presenceDb.toDouble(), "mid_q" to 1.0,
                        "high_shelf_freq" to 8000.0, "high_shelf_gain" to eq.highDb.toDouble(), "high_shelf_q" to 0.707
                    ))
                },
                // One control: threshold -10..-30 dB, ratio 1.5..5:1, 10/150 ms, 6 dB knee, make-up 40 % of the
                // reduction at the threshold, as in Tone.h.
                effect.compressor.takeIf { it.enabled }?.let { compressor ->
                    val amount = compressor.amount.coerceIn(0f, 1f).toDouble()
                    val threshold = -10.0 - 20.0 * amount
                    val ratio = 1.5 + 3.5 * amount
                    insert("compressor", mapOf(
                        "threshold" to threshold, "ratio" to ratio, "attack" to 10.0, "release" to 150.0, "knee" to 6.0,
                        "makeup" to 0.4 * -threshold * (1.0 - 1.0 / ratio), "mix" to 100.0
                    ))
                }
            )
            is ChainEffect.Reverb -> {
                val reverb = effect.reverb
                // My Audio crossfades with equal power (dry cos, wet sin), SoundCraft linearly (dry 1-m, wet m):
                // keep the wet/dry ratio and make up the dry level on the channel.
                val angle = reverb.mix.coerceIn(0f, 1f) * PI / 2
                val ratio = tan(angle).coerceAtMost(1e6)
                val m = ratio / (1.0 + ratio)
                if (m < 1.0) compensate(20.0 * log10(cos(angle).coerceAtLeast(1e-6) / (1.0 - m)))
                listOf(insert("room_reverb", mapOf(
                    "decay" to reverb.seconds.toDouble().coerceIn(0.1, 20.0),
                    "pre_delay" to reverb.preDelayMs.toDouble().coerceAtLeast(0.0),
                    // My Audio damping 0..1 shortens the highs; SoundCraft states the damping frequency.
                    "damping" to (16000.0 - 14000.0 * reverb.damping).coerceIn(1000.0, 20000.0),
                    "mix" to (m * 100.0).coerceIn(0.0, 100.0),
                    "size" to (50.0 * sqrt(reverb.seconds / 1.5)).coerceIn(10.0, 100.0)
                )))
            }
            else -> emptyList()
        }

    private fun insert(plugin: String, params: Map<String, Double>) = buildJsonObject {
        put("plugin", plugin)
        putJsonObject("params") { for ((key, value) in params.toSortedMap()) put(key, value) }
        put("bypass", false)
        put("active", true)
        put("preset", "")
    }

    /** Mono pan, or stereo balance as the two channel pans: the far channel moves toward the other side. */
    private fun pans(pan: Float, channels: Int): List<Double> {
        val p = pan.coerceIn(-1f, 1f).toDouble()
        return if (channels == 1) listOf(p) else listOf(-1.0 + 2.0 * max(0.0, p), 1.0 - 2.0 * max(0.0, -p))
    }

    /** [gain] in dB plus the channel's [compensation]; silence stays at SoundCraft's floor. */
    private fun compensated(gain: Float, compensation: Double): Double =
        if (gain <= 0f) SILENT_DB else (db(gain) + compensation).coerceIn(SILENT_DB, 12.0)

    private fun db(gain: Float): Double = if (gain <= 0f) SILENT_DB else (20.0 * log10(gain.toDouble())).coerceIn(SILENT_DB, 12.0)

    private fun source(id: Long, name: String, file: String, channels: Int, frames: Long) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("path", "$AUDIO_DIR/$file")
        put("channels", channels)
        put("frames", frames)
        put("sample_rate", 48000)
        put("format", "Wav")
        put("time_reference", 0)
        put("unsaved", false)
    }

    private fun clip(id: Long, name: String, start: Long, frames: Long, source: Long) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("start", start)
        put("length", frames)
        putJsonObject("content") { putJsonObject("Audio") { put("source", source); put("offset", 0) } }
        put("gain_db", 0.0)
        put("gain_env", JsonArray(emptyList()))
        put("fade_in", FADE)
        put("fade_out", FADE)
        put("muted", false)
        put("edit_locked", false)
        put("time_locked", false)
        put("rating", 0)
        put("sync_point", 0)
        put("color", JsonNull)
        put("auto_name", JsonNull)
        put("stretch", 1.0)
        put("group", JsonNull)
        put("loop_id", JsonNull)
    }

    private fun playlist(name: String, clips: List<JsonObject>) = buildJsonObject {
        put("name", name)
        put("clips", JsonArray(clips))
    }

    private fun mixer(volumeDb: Double, pans: List<Double>, mute: Boolean, solo: Boolean, inserts: List<JsonObject>) = buildJsonObject {
        put("volume_db", volumeDb)
        put("pan", JsonArray(pans.map(::JsonPrimitive)))
        put("mute", mute)
        put("solo", solo)
        put("solo_safe", false)
        put("phase_invert", false)
        put("input", "None")
        put("output", "Main")
        // SoundCraft has ten insert and ten send slots; inserts beyond ten cannot occur (two per My Audio slot at most).
        put("inserts", JsonArray(inserts.take(SLOTS) + List(SLOTS - inserts.size.coerceAtMost(SLOTS)) { JsonNull }))
        put("sends", JsonArray(List(SLOTS) { JsonNull }))
        put("automation_mode", "Read")
        put("trim_db", 0.0)
        put("vca", JsonNull)
        put("surround", JsonNull)
    }

    private fun track(id: Long, name: String, kind: String, format: String, color: IntArray, playlists: JsonArray,
                      mixer: JsonObject, automation: JsonArray) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("kind", kind)
        put("format", format)
        put("color", JsonArray(color.map { JsonPrimitive(it) }))
        put("height", "Medium")
        put("playlists", playlists)
        put("active_playlist", 0)
        put("mixer", mixer)
        put("automation", automation)
        put("view", "waveform")
        put("hidden", false)
        put("inactive", false)
        put("frozen", false)
        put("comments", "")
        put("folder", JsonNull)
        put("folder_open", true)
        put("instrument", JsonNull)
        put("ticks_timebase", false)
        put("elastic", JsonNull)
        put("pinned", false)
    }

    private fun ChainEffect.label(): String = when (this) {
        is ChainEffect.Airwindows -> name
        is ChainEffect.AmpModel -> name
        is ChainEffect.Cabinet -> name
        is ChainEffect.Unknown -> type
        is ChainEffect.Tone -> "EQ (My Audio)"
        is ChainEffect.Reverb -> "Hall (My Audio)"
    }

    private val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private val COLORS = listOf(
        intArrayOf(86, 132, 214), intArrayOf(94, 170, 214), intArrayOf(214, 150, 86), intArrayOf(120, 190, 110),
        intArrayOf(200, 110, 150), intArrayOf(170, 130, 210), intArrayOf(210, 190, 90), intArrayOf(110, 180, 170)
    )

    private val FADE = buildJsonObject {
        put("len", 0)
        put("shape", "EqualPower")
        put("curvature", 0.0)
    }

    /** Editor state of a new SoundCraft session (as `session.new` writes it in 0.20). */
    private val EDIT: JsonObject = Json.parseToJsonElement("""
        {"tool": "Smart", "edit_mode": "Slip", "selection": {"start": 0, "end": 0}, "selected_tracks": [],
         "selected_clips": [], "selected_sources": [], "playhead": 0,
         "grid": {"Note": {"value": "Sixteenth", "dotted": false, "triplet": false}}, "nudge": {"Seconds": 1.0},
         "grid_follow_main": false, "nudge_follow_main": false, "grid_clips_markers": false, "grid_lines": true,
         "main_counter": "MinSecs", "sub_counter": "Samples",
         "zoom": {"samples_per_px": 1024.0, "scroll": 0, "waveform_zoom": 1.0, "presets": [64.0, 256.0, 1024.0, 4096.0, 16384.0]},
         "zoom_toggle": null, "rulers": ["bars_beats", "min_secs", "tempo", "meter", "markers"],
         "link_timeline_edit": true, "link_track_edit": false, "insertion_follows_playback": false,
         "loop_playback": false, "pre_post_roll": false, "pre_roll": 96000, "post_roll": 96000,
         "tab_to_transient": false, "automation_follows_edit": true, "markers_follow_edit": false,
         "layered_editing": false, "mirrored_midi": false, "click": false, "countoff": false, "countoff_bars": 2,
         "midi_merge": false, "record_mode": "normal", "loop_record": false, "quickpunch": false, "scrolling": "page",
         "solo_mode": "sip", "pre_fader_metering": false, "delay_compensation": true, "keyboard_focus": "commands",
         "timeline_selection": {"start": 0, "end": 0},
         "flags": ["view.clip.gain_line", "view.clip.name", "view.clip.overlap_shadows", "view.marker.ruler_lines", "waveform.peak"],
         "values": {}}
    """.trimIndent()) as JsonObject
}
