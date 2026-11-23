package com.martensening.my_audio.audio

import java.io.Closeable
import java.io.File

/**
 * Resamples each channel independently, with identical timing, then interleaves it into PCM16. Works on blocks of
 * samples, so a long import does not pay per-sample calls.
 */
class ImportedAudioWriter(file: File, val inputRate: Int, val channels: Int, outputRate: Int) : Closeable {
    private val writer = PcmWavWriter(file, outputRate, channels)
    private val converters = Array(channels) { Resampler(inputRate, outputRate) }
    private val input = FloatArray(BLOCK)
    private val output = Array(channels) { FloatArray(maxOf(converters[0].maxOutputFor(BLOCK), converters[0].maxFinishOutput)) }
    private var interleaved = FloatArray(output[0].size * channels)
    private var finished = false
    val frames get() = writer.frames

    fun push(samples: FloatArray, frames: Int = samples.size / channels) {
        check(!finished)
        require(frames >= 0 && frames.toLong() * channels <= samples.size)
        var offset = 0
        while (offset < frames) {
            val count = minOf(BLOCK, frames - offset)
            emit { channel ->
                for (i in 0 until count) input[i] = samples[(offset + i) * channels + channel]
                converters[channel].process(input, count, output[channel])
            }
            offset += count
        }
    }

    private inline fun emit(convert: (Int) -> Int) {
        var frames = -1
        for (channel in 0 until channels) {
            val count = convert(channel)
            check(frames < 0 || frames == count) { "Channel timing differs" }
            frames = count
        }
        if (frames <= 0) return
        if (channels == 1) {
            writer.write(output[0], frames)
            return
        }
        if (interleaved.size < frames * channels) interleaved = FloatArray(frames * channels)
        for (i in 0 until frames) for (channel in 0 until channels) interleaved[i * channels + channel] = output[channel][i]
        writer.write(interleaved, frames * channels)
    }

    fun finish() {
        if (finished) return
        emit { channel -> converters[channel].finish(output[channel]) }
        finished = true
    }

    override fun close() {
        try { finish() } finally { writer.close() }
    }

    private companion object {
        const val BLOCK = 4096
    }
}
