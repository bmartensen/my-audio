package com.martensening.my_audio.platform

/**
 * A plain float output for short previews (effect auditions), independent of the native
 * transport: Android uses an AudioTrack, the desktop Java Sound. Interleaved samples -1..1.
 */
interface PcmOutput {
    /** Blocks until the samples are queued. */
    fun write(samples: FloatArray, count: Int)
    fun close()
}

/** Opens the default output, or null if the platform refuses it. */
expect fun openPcmOutput(sampleRate: Int, channels: Int): PcmOutput?
