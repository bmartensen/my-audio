package com.martensening.my_audio.audio

/**
 * Which channel of a two-channel input (e.g. a USB interface with mic on input 1 and instrument
 * on input 2) becomes the mono take. [code] matches `InputChannel` in `InputChannel.h`.
 */
enum class InputChannel(val code: Int) {
    First(0),
    Second(1),

    /** Both channels summed at unity gain; the default, so no connected source is silently lost. */
    Both(2);

    companion object {
        /** Native code for a single-channel input, opened in mono as before 0.8.1. */
        const val DEVICE_CODE = -1

        val DEFAULT = Both

        fun fromName(name: String?): InputChannel = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
