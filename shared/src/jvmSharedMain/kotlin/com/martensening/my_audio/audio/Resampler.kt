package com.martensening.my_audio.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Streaming sample-rate converter for mono audio: windowed-sinc (Kaiser) interpolation with a
 * tabulated kernel. Output sample n sits exactly at input position n · inRate / outRate (integer
 * arithmetic, no drift over long files). When converting down, the cut-off follows the output
 * Nyquist frequency so nothing aliases.
 */
class Resampler(private val inRate: Int, private val outRate: Int) {
    init {
        require(inRate > 0 && outRate > 0) { "Sample rates must be positive" }
    }

    private val passthrough = inRate == outRate
    /** Cut-off relative to the input Nyquist frequency, with a little room for the transition band. */
    private val cutoff = ROLLOFF * min(1.0, outRate.toDouble() / inRate)
    /** Kernel half-width in input samples. */
    private val half = ceil(ZERO_CROSSINGS / cutoff).toInt()
    private val table = FloatArray(2 * half * PHASES + 2).also { table ->
        val beta = KAISER_BETA
        val norm = besselI0(beta)
        for (i in table.indices) {
            val t = i.toDouble() / PHASES - half
            val x = t / half
            val window = if (x * x >= 1.0) 0.0 else besselI0(beta * sqrt(1 - x * x)) / norm
            val sinc = if (t == 0.0) 1.0 else sin(PI * cutoff * t) / (PI * cutoff * t)
            table[i] = (cutoff * sinc * window).toFloat()
        }
    }

    private var buffer = FloatArray(4096)
    /** Absolute input index of buffer[0]; samples before it are no longer needed. */
    private var bufferStart = 0L
    private var bufferCount = 0
    private var outputIndex = 0L
    private var finished = false

    /** Feeds [count] input samples; every output sample that can be computed already goes to [sink]. */
    fun push(samples: FloatArray, count: Int = samples.size, sink: (Float) -> Unit) {
        check(!finished) { "Resampler already finished" }
        if (passthrough) {
            for (i in 0 until count) sink(samples[i])
            outputIndex += count
            return
        }
        append(samples, count)
        drain(bufferStart + bufferCount, sink)
    }

    /** Ends the input; emits the remaining output so the result lasts exactly as long as the input. */
    fun finish(sink: (Float) -> Unit) {
        if (finished) return
        finished = true
        if (passthrough) return
        val total = bufferStart + bufferCount
        // Zeros after the end let the last samples see a complete kernel.
        append(FloatArray(half + 1), half + 1)
        drain(total + half + 1, sink, endFrames = outputFramesFor(total))
    }

    /** Output samples for [inputFrames] input samples. */
    fun outputFramesFor(inputFrames: Long) = (inputFrames * outRate + inRate - 1) / inRate

    private fun append(samples: FloatArray, count: Int) {
        // Drop what no further output sample can reach.
        val firstNeeded = (outputIndex * inRate / outRate) - half + 1
        val drop = (firstNeeded - bufferStart).coerceIn(0, bufferCount.toLong()).toInt()
        if (drop > 0) {
            System.arraycopy(buffer, drop, buffer, 0, bufferCount - drop)
            bufferCount -= drop
            bufferStart += drop
        }
        if (bufferCount + count > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, bufferCount + count))
        }
        System.arraycopy(samples, 0, buffer, bufferCount, count)
        bufferCount += count
    }

    private fun drain(available: Long, sink: (Float) -> Unit, endFrames: Long = Long.MAX_VALUE) {
        while (outputIndex < endFrames) {
            val position = outputIndex * inRate
            val centre = position / outRate
            // The kernel reaches input samples up to centre + half.
            if (centre + half >= available) break
            val fraction = (position % outRate).toDouble() / outRate
            var sum = 0.0
            for (k in centre - half + 1..centre + half) {
                val index = (k - bufferStart).toInt()
                // Before the first input sample the signal is silent.
                if (index < 0) continue
                val t = (centre - k) + fraction
                val tablePosition = (t + half) * PHASES
                val i = tablePosition.toInt()
                val weight = tablePosition - i
                val coefficient = table[i] + (table[i + 1] - table[i]) * weight
                sum += buffer[index] * coefficient
            }
            sink(sum.toFloat())
            outputIndex++
        }
    }

    private companion object {
        const val ZERO_CROSSINGS = 16
        const val PHASES = 512
        const val ROLLOFF = 0.95
        /** About 80 dB stop-band attenuation. */
        const val KAISER_BETA = 8.0

        fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val half = x / 2
            var k = 1
            while (term > 1e-12 * sum) {
                term *= (half / k) * (half / k)
                sum += term
                k++
            }
            return sum
        }
    }
}
