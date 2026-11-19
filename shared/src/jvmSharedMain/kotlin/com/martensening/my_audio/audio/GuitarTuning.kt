package com.martensening.my_audio.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.sin

/** Equal-tempered standard guitar, low to high, referenced to A4 = 440 Hz. */
object GuitarTuning {
    val midiNotes = listOf(40, 45, 50, 55, 59, 64)
    fun frequency(string: Int): Double = 440.0 * 2.0.pow((midiNotes[string] - 69) / 12.0)
    fun cents(frequency: Double, string: Int): Double = 1200.0 * ln(frequency / frequency(string)) / ln(2.0)
    fun nearestString(frequency: Double, previous: Int? = null): Int {
        val nearest = midiNotes.indices.minBy { abs(cents(frequency, it)) }
        // Keep the current target near a boundary until the new string is clearly closer.
        return previous?.takeIf { abs(cents(frequency, it)) <= abs(cents(frequency, nearest)) + 25.0 } ?: nearest
    }
}

/**
 * YIN cumulative mean normalized difference, followed by parabolic interpolation.
 * A fixed comparison length avoids favouring long lags. No FFT-bin quantisation or octave folding.
 * Runs on the analysis worker, never on the UI or transport callback.
 */
class PitchDetector(private val sampleRate: Int = 16_000, private val windowSize: Int = 2048) {
    private val maxLag = (sampleRate / 55.0).toInt() + 1
    private val minLag = (sampleRate / 500.0).toInt()
    private val difference = DoubleArray(maxLag + 1)
    private val filtered = FloatArray(windowSize)
    var confidence: Double = 0.0
        private set

    fun detect(samples: FloatArray): Double? {
        require(samples.size == windowSize && windowSize > maxLag * 2)
        confidence = 0.0
        var mean = 0.0
        var square = 0.0
        var clipped = 0
        for (sample in samples) {
            if (!sample.isFinite()) return null
            mean += sample
            square += sample * sample
            if (abs(sample) >= 0.98f) clipped++
        }
        mean /= windowSize
        if (square / windowSize - mean * mean < 0.00000004 || clipped > windowSize / 20) return null
        // Band-limit before periodicity analysis: high-frequency pick noise must not bury the
        // fundamental. Keep several guitar harmonics; DC is removed above/below, not amplified.
        val omega = 2 * Math.PI * 1200.0 / sampleRate
        val alpha = sin(omega) / kotlin.math.sqrt(2.0)
        val a0 = 1 + alpha
        val b0 = (1 - cos(omega)) / 2 / a0
        val b1 = 2 * b0
        val a1 = -2 * cos(omega) / a0
        val a2 = (1 - alpha) / a0
        var z1 = 0.0
        var z2 = 0.0
        for (i in samples.indices) {
            val input = samples[i] - mean
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b0 * input - a2 * output
            filtered[i] = output.toFloat()
        }
        val start = 64 // Exclude the filter startup transient from both comparison segments.
        val length = windowSize - maxLag
        var sum = 0.0
        difference[0] = 1.0
        for (lag in 1..maxLag) {
            var value = 0.0
            for (i in start until length) {
                val delta = (filtered[i] - filtered[i + lag]).toDouble()
                value += delta * delta
            }
            sum += value
            difference[lag] = if (sum == 0.0) 1.0 else value * lag / sum
        }
        // The first deep dip avoids harmonics. When room noise makes every dip shallower,
        // use the best local minimum, but require at least 70% periodicity and temporal agreement.
        var best = minLag
        for (candidate in minLag until maxLag) if (difference[candidate] < difference[best]) best = candidate
        val threshold = if (difference[best] < 0.15) 0.15 else minOf(0.30, difference[best] + 0.01)
        var lag = minLag
        while (lag < maxLag) {
            if (difference[lag] < threshold) {
                while (lag < maxLag && difference[lag + 1] < difference[lag]) lag++
                if (lag >= maxLag) return null
                val left = difference[lag - 1]
                val centre = difference[lag]
                val right = difference[lag + 1]
                val divisor = left - 2 * centre + right
                val correction = if (abs(divisor) > 1e-12) (0.5 * (left - right) / divisor).coerceIn(-1.0, 1.0) else 0.0
                confidence = (1 - centre).coerceIn(0.0, 1.0)
                return (sampleRate / (lag + correction)).takeIf { it in 55.0..500.0 }
            }
            lag++
        }
        return null
    }
}

/**
 * Three coherent measurements confirm a pitch before displaying it. A short hold rejects isolated
 * octave/onset errors; prolonged disagreement clears the reading. Adaptive smoothing in cents keeps
 * fine tuning quiet while following a deliberate peg turn quickly. Never snaps the result to a note.
 */
class PitchSmoother {
    private val recent = ArrayDeque<Double>()
    private var smoothed: Double? = null
    private var disagreements = 0
    private var missing = 0
    private var age = 0
    fun update(frequency: Double?): Double? {
        // Even intermittent valid-looking fragments must not keep an old note alive indefinitely.
        age++
        if (age > 4) smoothed = null
        if (frequency == null) {
            recent.clear()
            missing++
            if (missing >= 3) smoothed = null
            disagreements = 0
            return smoothed?.let { 440.0 * 2.0.pow(it / 1200.0) }
        }
        missing = 0
        recent.addLast(1200.0 * ln(frequency / 440.0) / ln(2.0))
        if (recent.size > 3) recent.removeFirst()
        if (recent.size < 3) return smoothed?.let { 440.0 * 2.0.pow(it / 1200.0) }
        val values = recent.sorted()
        if (values.last() - values.first() > 60.0) {
            disagreements++
            if (disagreements >= 3) smoothed = null
        } else {
            disagreements = 0
            age = 0
            val previous = smoothed
            val target = values[1]
            smoothed = if (previous == null || abs(target - previous) > 80.0) target else {
                val alpha = if (abs(target - previous) <= 8.0) 0.18 else 0.5
                previous + alpha * (target - previous)
            }
        }
        return smoothed?.let { 440.0 * 2.0.pow(it / 1200.0) }
    }
}
