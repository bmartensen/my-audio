package com.martensening.my_audio.audio

/**
 * Playback and recording devices of one transport as the engine needs them. The platform resolves
 * the user's selection into a route; a calibration belongs to exactly one route.
 */
interface TransportRoute {
    /** Platform output device id, 0 = system default. */
    val outputId: Int
    /** Platform input device id, 0 = system default. */
    val inputId: Int
    val outputBluetooth: Boolean
    /** True if any device of the route is Bluetooth (warm-up phase, wider stability limit). */
    val bluetooth: Boolean
    /** True for a hands-free (Bluetooth SCO) microphone. */
    val communication: Boolean
    /** Channel of a two-channel input that is recorded; null opens the input in mono (single-channel devices). */
    val inputChannel: InputChannel? get() = null

    /** Both devices are known and usable together. */
    val ready: Boolean

    val hasOutput: Boolean
    val hasInput: Boolean

    /** Same devices on the same ports, i.e. the calibration still applies. */
    fun samePorts(other: TransportRoute): Boolean

    /** Same playback device; playback alone does not depend on the microphone. */
    fun sameOutput(other: TransportRoute): Boolean

    /** Playback only: playback never opens a microphone link. */
    fun withoutInput(): TransportRoute
}
