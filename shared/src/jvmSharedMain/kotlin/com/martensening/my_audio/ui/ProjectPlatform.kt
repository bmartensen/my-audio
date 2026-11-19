package com.martensening.my_audio.ui

import com.martensening.my_audio.audio.TransportRoute
import com.martensening.my_audio.data.EffectLibrary
import com.martensening.my_audio.data.ProjectRepository
import java.io.File

/** An audio file chosen for "Import track"; the platform knows how to read and decode it. */
interface ImportSource {
    /** File name without extension, or null if unknown. */
    val displayName: String?

    /** Decodes into [target] (mono/stereo, 16 bit, [sampleRate]); returns the frames written. */
    suspend fun decodeTo(target: File, sampleRate: Int): Long
}

/**
 * What a [ProjectSession] needs from the platform: devices and their calibration, keeping a
 * transport alive in the background, encoding the shared mix and the tuner input.
 */
interface ProjectPlatform {
    val repository: ProjectRepository

    /** The effect selection of this device ("Add effect" offers only these). */
    val effectLibrary: EffectLibrary

    /** Scratch space for exports; the latest export replaces the previous one. */
    val cacheDir: File

    /** The devices the user selected, resolved to the current ports. */
    fun route(): TransportRoute

    /** Calibrated round-trip latency of [route] in project frames, or null if it was never measured. */
    fun latencyFrames(route: TransportRoute): Long?

    /** Prepares the platform routing for a transport (e.g. a Bluetooth link); false if impossible. */
    suspend fun startRouting(route: TransportRoute): Boolean

    fun stopRouting()

    /** Keeps a running transport alive in the background; false if that is impossible right now. */
    suspend fun beginTransport(session: ProjectSession, recording: Boolean): Boolean

    fun endTransport()

    /** Writes interleaved float [samples] as Ogg/Opus to [target]. */
    suspend fun encodeMix(samples: FloatArray, sampleRate: Int, channels: Int, target: File)

    /**
     * Runs the tuner input until cancelled: [onReady] receives the microphone name, [onPitch]
     * frequency (null = none), level and whether a note is tracked. [onInterrupted] closes it.
     */
    suspend fun runTuner(onInterrupted: () -> Unit, onReady: (String) -> Unit, onPitch: (Double?, Float, Boolean) -> Unit)
}
