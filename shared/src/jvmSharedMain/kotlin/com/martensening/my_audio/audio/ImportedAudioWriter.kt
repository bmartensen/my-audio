package com.martensening.my_audio.audio

import java.io.Closeable
import java.io.File

/** Resamples each channel independently, with identical timing, then interleaves it into PCM16. */
class ImportedAudioWriter(file: File, val inputRate: Int, val channels: Int, outputRate: Int) : Closeable {
    private val writer = PcmWavWriter(file, outputRate, channels)
    private val converters = Array(channels) { Resampler(inputRate, outputRate) }
    private val input = FloatArray(4096)
    private val output = Array(channels) { FloatArray(converters[0].outputFramesFor(4096).toInt() + 128) }
    private var finished = false
    val frames get() = writer.frames

    fun push(samples: FloatArray, frames: Int = samples.size / channels) {
        check(!finished)
        require(frames >= 0 && frames.toLong() * channels <= samples.size)
        var offset = 0
        while (offset < frames) {
            val count = minOf(input.size, frames - offset)
            emit { channel, sink ->
                for (i in 0 until count) input[i] = samples[(offset + i) * channels + channel]
                converters[channel].push(input, count, sink)
            }
            offset += count
        }
    }

    private fun emit(convert: (Int, (Float) -> Unit) -> Unit) {
        var frames = -1
        for (channel in 0 until channels) {
            var count = 0
            convert(channel) { sample ->
                if (count == output[channel].size) output[channel] = output[channel].copyOf(count * 2)
                output[channel][count++] = sample
            }
            check(frames < 0 || frames == count) { "Channel timing differs" }
            frames = count
        }
        for (i in 0 until frames) for (channel in 0 until channels) writer.write(output[channel][i])
    }

    fun finish() {
        if (finished) return
        emit { channel, sink -> converters[channel].finish(sink) }
        finished = true
    }

    override fun close() {
        try { finish() } finally { writer.close() }
    }
}
