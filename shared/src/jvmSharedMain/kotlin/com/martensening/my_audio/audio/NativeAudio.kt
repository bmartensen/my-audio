package com.martensening.my_audio.audio

import com.martensening.my_audio.platform.loadNativeEngine

/** JNI entry points of the native Oboe engine (`app/src/main/cpp`). */
object NativeAudio {
    /** Fixed engine and project sample rate (`MultitrackEngine::kSampleRate`). */
    const val SAMPLE_RATE = 48_000

    init {
        loadNativeEngine()
    }

    /**
     * Runs one blocking full-duplex calibration session; layout see [SessionMeasurement.fromNative].
     * [inputChannel] is an [InputChannel.code], or [InputChannel.DEVICE_CODE] for a single-channel input.
     */
    external fun runCalibrationSession(outputDeviceId: Int, inputDeviceId: Int, bluetooth: Boolean, handsFreeInput: Boolean,
                                       inputChannel: Int): DoubleArray

    /**
     * Loads mono/stereo 16-bit WAV tracks and renders the mix with their processing; fails while a stream
     * is running. Per track, [paths] holds take, reference and clean-cache path ("" = none),
     * [frames] offset and count-in frames, [parameters] five values: gain, metronome bpm (0 = keep click),
     * metronome volume, normalisation target dB (NaN = off), pan; [chains] the encoded effect chain
     * (see `AudioEngine.encodeChain`). [master] is the encoded chain on the whole mix.
     */
    external fun setTracks(paths: Array<String>, frames: LongArray, parameters: FloatArray, chains: Array<FloatArray>,
                           envelopes: Array<LongArray>, envelopeGains: Array<FloatArray>, master: FloatArray): Boolean

    /** Cleaned, aligned WAV per track, preserving mono/stereo (same layout as [setTracks]); blocks. */
    external fun exportStems(paths: Array<String>, frames: LongArray, parameters: FloatArray, chains: Array<FloatArray>,
                             envelopes: Array<LongArray>, envelopeGains: Array<FloatArray>,
                             projectFrames: Long, targets: Array<String>): Boolean

    external fun startPlayback(outputDeviceId: Int, bluetooth: Boolean): Boolean

    /**
     * Plays all tracks and records the microphone into a new WAV file at [path]. The take starts
     * [countInFrames] before project frame 0 so the metronome can count in. [inputChannel] as for
     * [runCalibrationSession]; the take is always mono.
     */
    external fun startRecording(path: String, referencePath: String, countInFrames: Long,
                                outputDeviceId: Int, inputDeviceId: Int, handsFreeInput: Boolean, bluetooth: Boolean,
                                inputChannel: Int): Boolean

    /**
     * Live audition: the microphone through the monitor chain to the output, nothing else played or recorded.
     * Same streams as a take; [stop] ends it.
     */
    external fun startMonitoring(outputDeviceId: Int, inputDeviceId: Int, handsFreeInput: Boolean, bluetooth: Boolean,
                                 inputChannel: Int): Boolean

    /**
     * The chain the microphone is monitored through, while recording or in live audition: encoded [chain]
     * (see `AudioEngine.encodeChain`), then [gain] and mono [pan]. Disabled means nothing is monitored.
     * Builds the chain on the calling thread (may take a moment) and switches with a short crossfade.
     */
    external fun setMonitorChain(chain: FloatArray, gain: Float, pan: Float, enabled: Boolean)

    /** Global stream buffer: 0 = system default, 1/2/4 = small/medium/large; see `LiveChain.h`. */
    external fun setBufferSetting(setting: Int)

    /** Actual open stream ports; checked throughout transport to detect silent rerouting. */
    external fun currentDeviceIds(): IntArray
    external fun transportXruns(): Int

    /** Stops playback or recording; returns the recorded frame count, or -1 if nothing was recorded. */
    external fun stop(): Long

    /** Project frames rendered since the stream started. */
    external fun positionFrames(): Long

    /** False when idle, when playback reached the end, or after a stream error. */
    external fun isActive(): Boolean

    /** Highest input level since the previous call (0..1). */
    external fun takeInputPeak(): Float

    /** Metronome click on top of the mix; takes effect immediately, also while a stream runs. */
    external fun setMetronome(enabled: Boolean, bpm: Float, volume: Float)

    /** Exactly what playback would output (mix plus audible click); empty while a stream runs. */
    external fun renderedMix(): FloatArray

    /** Jumps a running playback to [frame]; ignored while recording. */
    external fun seekPlayback(frame: Long)

    /** Practice loop of the playback, [start, end) in project frames; end <= start switches it off. */
    external fun setLoop(start: Long, end: Long)

    /** Name, category and original description of every Airwindows effect, in catalog order. */
    external fun airwindowsCatalog(): Array<String>

    external fun airwindowsParameterNames(index: Int): Array<String>

    /** Default parameter values 0..1. */
    external fun airwindowsDefaults(index: Int): FloatArray

    /** The effect's own display text (value and unit) for each parameter value. */
    external fun airwindowsDisplay(index: Int, values: FloatArray): Array<String>

    /**
     * Effect preview source: [lengthFrames] of the first track in the [setTracks] layout from project
     * frame [startFrame], cleaned and normalised as the mix does, before its effects. Blocks.
     */
    external fun previewTrack(paths: Array<String>, frames: LongArray, parameters: FloatArray, chains: Array<FloatArray>,
                              envelopes: Array<LongArray>, envelopeGains: Array<FloatArray>,
                              startFrame: Long, lengthFrames: Long): Boolean

    /** Effect preview source: the sum of the tracks before the master chain; the master stage follows. Blocks. */
    external fun previewMix(paths: Array<String>, frames: LongArray, parameters: FloatArray, chains: Array<FloatArray>,
                            envelopes: Array<LongArray>, envelopeGains: Array<FloatArray>,
                            startFrame: Long, lengthFrames: Long): Boolean

    /** Effect preview source: interleaved samples with [channels] at [SAMPLE_RATE]. */
    external fun previewSamples(samples: FloatArray, channels: Int): Boolean

    /** The preview source through the encoded [chain] as a seamless interleaved stereo loop; empty without a source. */
    external fun previewRender(chain: FloatArray): FloatArray

    /**
     * Checks a tone file of [kind] (1 amp model, 2 cabinet): "1"/"0", error, sample rate, IR frames at 48 kHz,
     * NAM metadata JSON. Blocks; does not register the file.
     */
    external fun inspectTone(path: String, kind: Int): Array<String>

    /** Loads a tone file once for effect chains; its handle, or -1 if it cannot be used. Blocks. */
    external fun loadTone(path: String, kind: Int): Int
}
