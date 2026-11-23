package com.martensening.my_audio.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Streaming sample-rate converter for mono audio: windowed-sinc (Kaiser) interpolation. Output sample n sits
 * exactly at input position n · inRate / outRate (integer arithmetic, no drift over long files). When converting
 * down, the cut-off follows the output Nyquist frequency so nothing aliases.
 *
 * The rates' ratio repeats after outRate / gcd output samples (160 for 44.1 → 48 kHz), so the kernel is evaluated
 * once per such phase into a filter bank; each output sample is then one dot product. Unusual ratios with very
 * many phases interpolate a finely tabulated kernel instead.
 */
class Resampler(private val inRate: Int, private val outRate: Int) {
    init {
        require(inRate > 0 && outRate > 0) { "Sample rates must be positive" }
    }

    private val passthrough = inRate == outRate
    /** Cut-off relative to the input Nyquist frequency, with a little room for the transition band. */
    private val cutoff = ROLLOFF * min(1.0, outRate.toDouble() / inRate)
    /** Kernel half-width in input samples; each output sample reads 2 · half input samples. */
    private val half = ceil(ZERO_CROSSINGS / cutoff).toInt()
    private val taps = 2 * half
    private val divisor = gcd(inRate, outRate)
    /** Input step and phase count of the rate ratio in lowest terms. */
    private val step = (inRate / divisor).toLong()
    private val phases = outRate / divisor
    private val norm = besselI0(KAISER_BETA)

    /** The windowed sinc at [t] input samples from the output position. */
    private fun kernel(t: Double): Double {
        val x = t / half
        val window = if (x * x >= 1.0) 0.0 else besselI0(KAISER_BETA * sqrt(1 - x * x)) / norm
        val sinc = if (t == 0.0) 1.0 else sin(PI * cutoff * t) / (PI * cutoff * t)
        return cutoff * sinc * window
    }

    /** Per phase p, the weights of input samples centre - half + 1 + m (m = 0 until taps). */
    private val bank: FloatArray? = if (passthrough || phases > MAX_PHASES) null else FloatArray(phases * taps).also { bank ->
        for (p in 0 until phases) {
            val fraction = p.toDouble() / phases
            for (m in 0 until taps) bank[p * taps + m] = kernel((half - 1 - m) + fraction).toFloat()
        }
    }

    /** Fallback for ratios with too many phases: the kernel sampled at PHASES points per input sample. */
    private val table: FloatArray? = if (passthrough || bank != null) null else
        FloatArray(taps * PHASES + 2) { kernel(it.toDouble() / PHASES - half).toFloat() }

    private var buffer = FloatArray(4096)
    /** Absolute input index of buffer[0]; samples before it are no longer needed. */
    private var bufferStart = 0L
    private var bufferCount = 0
    private var outputIndex = 0L
    private var finished = false

    /** Output samples at most produced by one [process] call with [count] input samples. */
    fun maxOutputFor(count: Int): Int = (count.toLong() * outRate / inRate + taps + 2).toInt()

    /** Output samples at most produced by [finish]. */
    val maxFinishOutput get() = maxOutputFor(half + 1)

    /**
     * Feeds [count] input samples and writes every output sample that can be computed already to [output] from
     * [offset] (room for [maxOutputFor] samples); returns how many were written.
     */
    fun process(samples: FloatArray, count: Int, output: FloatArray, offset: Int = 0): Int {
        check(!finished) { "Resampler already finished" }
        if (passthrough) {
            System.arraycopy(samples, 0, output, offset, count)
            outputIndex += count
            return count
        }
        append(samples, count)
        return drain(bufferStart + bufferCount, output, offset)
    }

    /** Ends the input; writes the remaining output (at most [maxFinishOutput] samples) so the result lasts as long as the input. */
    fun finish(output: FloatArray, offset: Int = 0): Int {
        if (finished) return 0
        finished = true
        if (passthrough) return 0
        val total = bufferStart + bufferCount
        // Zeros after the end let the last samples see a complete kernel.
        append(FloatArray(half + 1), half + 1)
        return drain(total + half + 1, output, offset, endFrames = outputFramesFor(total))
    }

    /** Feeds [count] input samples; every output sample that can be computed already goes to [sink]. */
    fun push(samples: FloatArray, count: Int = samples.size, sink: (Float) -> Unit) {
        val output = FloatArray(maxOutputFor(count))
        val written = process(samples, count, output)
        for (i in 0 until written) sink(output[i])
    }

    /** Ends the input; emits the remaining output so the result lasts exactly as long as the input. */
    fun finish(sink: (Float) -> Unit) {
        val output = FloatArray(maxFinishOutput)
        val written = finish(output)
        for (i in 0 until written) sink(output[i])
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

    private fun drain(available: Long, output: FloatArray, offset: Int, endFrames: Long = Long.MAX_VALUE): Int {
        var written = 0
        val bank = bank
        while (outputIndex < endFrames) {
            val position = outputIndex * step
            val centre = position / phases
            // The kernel reaches input samples up to centre + half.
            if (centre + half >= available) break
            val phase = (position % phases).toInt()
            val first = (centre - half + 1 - bufferStart).toInt()
            var sum = 0f
            if (bank != null) {
                val weights = phase * taps
                // Before the first input sample the signal is silent.
                val from = if (first < 0) -first else 0
                for (m in from until taps) sum += buffer[first + m] * bank[weights + m]
            } else {
                val table = table!!
                val shift = phase.toDouble() / phases * PHASES
                val base = shift.toInt()
                val weight = (shift - base).toFloat()
                for (m in 0 until taps) {
                    val index = first + m
                    if (index < 0) continue
                    val i = (taps - 1 - m) * PHASES + base
                    sum += buffer[index] * (table[i] + (table[i + 1] - table[i]) * weight)
                }
            }
            output[offset + written++] = sum
            outputIndex++
        }
        return written
    }

    private companion object {
        const val ZERO_CROSSINGS = 16
        const val PHASES = 512
        /** Largest filter bank (phases); 44.1 → 48 kHz needs 160, 22.05 → 48 kHz 320. */
        const val MAX_PHASES = 4096
        const val ROLLOFF = 0.95
        /** About 80 dB stop-band attenuation. */
        const val KAISER_BETA = 8.0

        tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

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
