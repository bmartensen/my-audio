package com.martensening.my_audio.data

import kotlinx.serialization.json.JsonObject
import kotlin.math.floor

/**
 * Per-project click track; off by default so a new project records cleanly right away. The click
 * sounds while recording; during playback only if [duringPlayback] is set.
 */
data class Metronome(
    val enabled: Boolean = false,
    val bpm: Int = DEFAULT_BPM,
    val volume: Float = DEFAULT_VOLUME,
    val duringPlayback: Boolean = false
) {
    /**
     * Length of the one-bar count-in before project frame 0. Matches the native click grid
     * (`-ClickGrid::beatStart(-COUNT_IN_BEATS)`), so the count-in starts exactly on a click.
     */
    fun countInFrames(sampleRate: Int): Long = floor(COUNT_IN_BEATS * 60.0 * sampleRate / bpm.toFloat()).toLong()

    companion object {
        /** One 4/4 bar; its clicks are recorded without music and serve as removal templates. */
        const val COUNT_IN_BEATS = 4
        const val DEFAULT_BPM = 120
        const val DEFAULT_VOLUME = 0.15f
        const val MIN_BPM = 40
        const val MAX_BPM = 240
    }
}

/** Peak normalisation to [targetDb] dBFS. */
data class NormalizeEffect(val enabled: Boolean = false, val targetDb: Float = DEFAULT_TARGET_DB) {
    companion object {
        const val DEFAULT_TARGET_DB = -6f
        const val MIN_TARGET_DB = -24f
        const val MAX_TARGET_DB = 0f
    }
}

/**
 * Algorithmic reverb from small chamber to cathedral: decay time [seconds] (RT60), [preDelayMs]
 * before the first reflection, high-frequency [damping] (0 = stone, 1 = soft) and wet amount
 * [mix] (0 = dry, 0.5 = equal energy, 1 = wet only).
 */
data class ReverbEffect(
    val seconds: Float = Preset.Hall.seconds,
    val preDelayMs: Float = Preset.Hall.preDelayMs,
    val damping: Float = Preset.Hall.damping,
    val mix: Float = Preset.Hall.mix
) {
    enum class Preset(val seconds: Float, val preDelayMs: Float, val damping: Float, val mix: Float) {
        Chamber(0.4f, 2f, 0.6f, 0.2f),
        Room(0.8f, 8f, 0.5f, 0.22f),
        Hall(1.8f, 20f, 0.4f, 0.25f),
        Church(3.5f, 35f, 0.35f, 0.3f),
        Cathedral(7f, 60f, 0.3f, 0.35f)
    }

    fun with(preset: Preset) =
        copy(seconds = preset.seconds, preDelayMs = preset.preDelayMs, damping = preset.damping, mix = preset.mix)

    companion object {
        const val MIN_SECONDS = 0.2f
        const val MAX_SECONDS = 10f
        const val MAX_PRE_DELAY_MS = 200f
    }
}

/** Three-band tone control in dB: low shelf 150 Hz, presence peak 3 kHz, high shelf 8 kHz. */
data class EqEffect(
    val enabled: Boolean = false,
    val lowDb: Float = 0f,
    val presenceDb: Float = 0f,
    val highDb: Float = 0f
) {
    companion object {
        const val RANGE_DB = 12f
    }
}

/** Leveller with one control: [amount] 0..1 sets threshold and ratio together; make-up is automatic. */
data class CompressorEffect(val enabled: Boolean = false, val amount: Float = DEFAULT_AMOUNT) {
    companion object {
        const val DEFAULT_AMOUNT = 0.4f
    }
}

/** Starting points for EQ and compressor, like the reverb rooms; values stay editable. */
enum class TonePreset(val lowDb: Float, val presenceDb: Float, val highDb: Float, val compressor: Float) {
    Vocals(-3f, 3f, 2f, 0.5f),
    Guitar(-2f, 1.5f, 1f, 0.3f),
    Piano(0f, 0f, 1.5f, 0.2f),
    Neutral(0f, 0f, 0f, 0.3f);

    fun eq() = EqEffect(enabled = true, lowDb = lowDb, presenceDb = presenceDb, highDb = highDb)
    fun compressor() = CompressorEffect(enabled = true, amount = compressor)
}

/** An effect that can sit in an effect chain of a track or of the master. */
sealed interface ChainEffect {
    /**
     * „EQ (My Audio)“: the three-band EQ followed by the compressor, set together by a [TonePreset].
     * Each part keeps its own switch; the slot sounds as long as one part is on.
     */
    data class Tone(
        val eq: EqEffect = TonePreset.Neutral.eq(),
        val compressor: CompressorEffect = TonePreset.Neutral.compressor()
    ) : ChainEffect {
        val audible get() = eq.enabled || compressor.enabled

        fun with(preset: TonePreset) = Tone(preset.eq(), preset.compressor())

        /** The preset whose values all parts match exactly, or null for custom values. */
        val preset get() = TonePreset.entries.firstOrNull { it.eq() == eq && it.compressor() == compressor }
    }

    /** „Hall (My Audio)“: the stereo reverb with its room presets. */
    data class Reverb(val reverb: ReverbEffect = ReverbEffect()) : ChainEffect

    /**
     * An effect of the built-in Airwindows catalog, by its [name] (stable across builds), with one
     * value 0..1 per parameter. A name this build does not know stays stored but is not rendered.
     * [levelMatch] brings the output back to the loudness of the input, so switching the effect on
     * changes the sound but not the balance of the mix.
     */
    data class Airwindows(val name: String, val values: List<Float>, val levelMatch: Boolean = true) : ChainEffect

    /**
     * An imported amp model (Neural Amp Modeler profile) from the tone library, referenced by the [tone] content
     * hash; [name] is shown when the tone is missing. Gains in dB around the model.
     */
    data class AmpModel(
        val tone: String,
        val name: String,
        val inputDb: Float = 0f,
        val outputDb: Float = 0f,
        val levelMatch: Boolean = true
    ) : ChainEffect {
        companion object {
            const val GAIN_RANGE_DB = 24f
        }
    }

    /** An imported cabinet impulse response from the tone library, referenced like [AmpModel]. */
    data class Cabinet(val tone: String, val name: String, val levelMatch: Boolean = true) : ChainEffect

    /**
     * An effect [type] that only a newer app version knows. Its slot JSON ([fields], without `enabled`) is kept
     * verbatim and written back, so the effect survives editing here; it is not rendered by this version.
     */
    data class Unknown(val type: String, val fields: JsonObject) : ChainEffect
}

/** One insert of an effect chain; switching it off keeps its settings. */
data class EffectSlot(
    val effect: ChainEffect,
    val enabled: Boolean = true,
    val unknown: UnknownFields = UnknownFields.NONE
) {
    /** Whether the slot changes the sound in this version; an effect only a newer version knows does not. */
    val audible get() = enabled && effect !is ChainEffect.Unknown && (effect !is ChainEffect.Tone || effect.audible)

    companion object {
        /** Inserts per track and on the master. */
        const val MAX_PER_CHAIN = 6
    }
}

/**
 * Non-destructive track processing, applied in this order when the mix is rendered: metronome
 * removal, bleed removal, normalisation, the effect [chain] in its order, then volume × envelope
 * and pan.
 */
data class TrackEffects(
    /** Only relevant for takes with a [Track.recordedMetronome]. */
    val removeMetronome: Boolean = false,
    /** Removes the speaker playback of the other tracks; only relevant for takes with a reference. */
    val removeBleed: Boolean = false,
    val normalize: NormalizeEffect = NormalizeEffect(),
    val chain: List<EffectSlot> = emptyList(),
    val unknown: UnknownFields = UnknownFields.NONE
) {
    companion object {
        /** Imported audio has no recorded click and no playback reference, so nothing is removed. */
        val IMPORTED = TrackEffects(removeMetronome = false, removeBleed = false)
    }
}

/**
 * One recorded take. [offsetFrames] is the project frame of the file's first sample; overdubs
 * start negative by the calibrated latency so the take lines up with the existing tracks.
 * Volume, mute and effects are applied when the mix is rendered, never to the file.
 */
data class Track(
    val id: Long,
    val name: String,
    val fileName: String,
    val offsetFrames: Long,
    val lengthFrames: Long,
    val volume: Float = 1f,
    val muted: Boolean = false,
    /** While any track is soloed, exactly the soloed tracks sound; [muted] stays stored as it is. */
    val solo: Boolean = false,
    /** Mono panorama or stereo balance: -1 left, 0 centre, 1 right. */
    val pan: Float = 0f,
    val createdAtMillis: Long,
    /**
     * Metronome that was audible while this take was recorded, or null. It reproduces the
     * recorded click exactly, so the mix can remove it again.
     */
    val recordedMetronome: Metronome? = null,
    /** Count-in recorded at the start of the file, before the latency; 0 for takes without metronome. */
    val countInFrames: Long = 0,
    /**
     * Exactly what the speaker played during the take (mix incl. the effects of that moment, no
     * click), sample-aligned with the take file; null for takes recorded over silence.
     */
    val referenceFileName: String? = null,
    val effects: TrackEffects = TrackEffects(),
    val volumeEnvelope: List<VolumePoint> = emptyList(),
    /** PCM channel count; microphone takes and old projects are mono. */
    val channels: Int = 1,
    /**
     * False for a take recorded on a device pair that was not calibrated: [offsetFrames] then holds only the
     * count-in, without latency compensation. Later calibrations never move it.
     */
    val latencyCalibrated: Boolean = true,
    val unknown: UnknownFields = UnknownFields.NONE
) {
    val endFrames get() = offsetFrames + lengthFrames
}

/**
 * The empty record track at the end of the project: settings prepared for the next take, without audio.
 * Record captures into it; the take then becomes a [Track] with these settings, unmuted. A new record track
 * starts [muted], which also means its microphone is not monitored; unmuting it monitors the input.
 */
data class RecordTrack(
    /** Null shows and records under the default name for the next track number. */
    val name: String? = null,
    val volume: Float = 1f,
    val muted: Boolean = true,
    val solo: Boolean = false,
    val pan: Float = 0f,
    val effects: TrackEffects = TrackEffects(),
    val volumeEnvelope: List<VolumePoint> = emptyList(),
    val unknown: UnknownFields = UnknownFields.NONE
) {
    /** Matches a newly prepared track; unknown fields do not count. */
    val isPristine get() = copy(unknown = UnknownFields.NONE) == RecordTrack()

    companion object {
        /** The settings that stay when only the recording of [track] is deleted. */
        fun keepingSettingsOf(track: Track) = RecordTrack(
            name = track.name,
            volume = track.volume,
            muted = track.muted,
            solo = track.solo,
            pan = track.pan,
            effects = track.effects,
            volumeEnvelope = track.volumeEnvelope
        )
    }
}

/**
 * Practice loop: playback repeats project frames [startFrames, endFrames) while [enabled].
 * Recording always starts at the beginning and never loops.
 */
data class PlaybackLoop(val startFrames: Long, val endFrames: Long, val enabled: Boolean = true) {
    val lengthFrames get() = endFrames - startFrames
}

/**
 * Teleprompter for the notes: while Play or Record runs, the open notes glide down by one text
 * line every [secondsPerLine]. Start and stop never move the position, they only pause the motion.
 */
data class NotesScroll(val enabled: Boolean = false, val secondsPerLine: Float = DEFAULT_SECONDS_PER_LINE) {
    companion object {
        const val DEFAULT_SECONDS_PER_LINE = 4f
        const val MIN_SECONDS_PER_LINE = 0.5f
        const val MAX_SECONDS_PER_LINE = 20f
        const val STEP_SECONDS_PER_LINE = 0.5f
        /** The first lines are on screen at the start; the text rests this many lines before gliding. */
        const val LEAD_IN_LINES = 4
    }
}

/**
 * Silent countdown before a take, to get into position at the instrument. Nothing plays or
 * records meanwhile; a metronome count-in follows afterwards as usual.
 */
data class PreRoll(val enabled: Boolean = false, val seconds: Int = DEFAULT_SECONDS) {
    companion object {
        const val DEFAULT_SECONDS = 5
        const val MIN_SECONDS = 1
        const val MAX_SECONDS = 30
    }
}

data class Project(
    val id: Long,
    val name: String,
    val createdAtMillis: Long,
    val sampleRate: Int,
    val nextTrackNumber: Int = 1,
    val tracks: List<Track> = emptyList(),
    val metronome: Metronome = Metronome(),
    val loop: PlaybackLoop? = null,
    /** One shared song sheet per project; whitespace preserves chord placement. */
    val notes: String = "",
    /** Text size of the notes in sp, kept per project. */
    val notesTextSize: Int = DEFAULT_NOTES_TEXT_SIZE,
    val notesScroll: NotesScroll = NotesScroll(),
    val preRoll: PreRoll = PreRoll(),
    /** Effect chain on the whole mix, before the fixed loudness and limiter stage. */
    val masterChain: List<EffectSlot> = emptyList(),
    /**
     * The prepared empty track after [tracks], or null to show the add-track action.
     * Recording without a prepared track uses [nextTake] directly.
     */
    val recordTrack: RecordTrack? = null,
    val unknown: UnknownFields = UnknownFields.NONE
) {
    companion object {
        const val DEFAULT_NOTES_TEXT_SIZE = 18
        const val MIN_NOTES_TEXT_SIZE = 12
        const val MAX_NOTES_TEXT_SIZE = 30
        const val NOTES_TEXT_SIZE_STEP = 2
    }

    /** Project length in frames; audio before project frame 0 is not part of the project. */
    val lengthFrames get() = tracks.maxOfOrNull { it.endFrames }?.coerceAtLeast(0L) ?: 0L

    /**
     * Settings the next take starts from: the empty record track, or, when the last track already holds a take,
     * a new muted track inheriting that track's effect chain and input settings.
     */
    val nextTake: RecordTrack get() = recordTrack ?: tracks.lastOrNull()?.let { RecordTrack(effects = it.effects) } ?: RecordTrack()

    /** A solo on the empty record track also counts: then only its monitored input is heard. */
    private val anySolo get() = tracks.any { it.solo } || recordTrack?.solo == true

    /** Tracks that Play, Record, the overdub reference and Share include: the soloed ones, else the unmuted. */
    val audibleTracks get() = if (anySolo) tracks.filter { it.solo } else tracks.filterNot { it.muted }

    fun isAudible(track: Track) = if (anySolo) track.solo else !track.muted

    /** The loop when it is switched on and still lies within the project. */
    val activeLoop get() = loop?.takeIf { it.enabled && it.startFrames >= 0 && it.endFrames <= lengthFrames && it.lengthFrames > 0 }
}
