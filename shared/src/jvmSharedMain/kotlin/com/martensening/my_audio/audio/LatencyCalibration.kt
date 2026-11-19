package com.martensening.my_audio.audio

/** Result of one native calibration session (see `CalibrationSession.h`). */
data class SessionMeasurement(
    val status: Int,
    val sampleRate: Int,
    val validChirps: Int,
    val latencyFrames: Double,
    val chirpSpreadFrames: Double,
    val minSnrDb: Double,
    val timestampLatencyFrames: Double?,
    val xruns: Int,
    val outputMMap: Boolean,
    val inputMMap: Boolean,
    val framesPerBurst: Int,
    val outputBufferFrames: Int
) {
    val isValid get() = status == STATUS_OK

    companion object {
        const val STATUS_OK = 0
        const val STATUS_TIMEOUT = -4
        const val STATUS_NO_SIGNAL = -5

        fun fromNative(values: DoubleArray) = SessionMeasurement(
            status = values[0].toInt(),
            sampleRate = values[1].toInt(),
            validChirps = values[2].toInt(),
            latencyFrames = values[3],
            chirpSpreadFrames = values[4],
            minSnrDb = values[5],
            timestampLatencyFrames = values[6].takeUnless { it.isNaN() },
            xruns = values[7].toInt(),
            outputMMap = values[8] != 0.0,
            inputMMap = values[9] != 0.0,
            framesPerBurst = values[10].toInt(),
            outputBufferFrames = values[11].toInt()
        )
    }
}

/** Calibration aggregated over several sessions; times in milliseconds. */
data class CalibrationResult(
    val latencyFrames: Double,
    val sampleRate: Int,
    val sessionSpreadMs: Double,
    val chirpSpreadMs: Double,
    /** Timestamp-based estimate of the same latency, if the streams reported timestamps. */
    val timestampLatencyMs: Double?,
    /** Spread of (measured - timestamp estimate) across sessions; small means timestamps track session changes. */
    val timestampDeviationSpreadMs: Double?,
    val minSnrDb: Double,
    val sessions: Int,
    val xruns: Int,
    val mmap: Boolean,
    val measuredAtMillis: Long
) {
    val latencyMs get() = latencyFrames * 1000.0 / sampleRate

    /** Stability: the larger spread between stream starts or between the chirps of one session. */
    val spreadMs get() = maxOf(sessionSpreadMs, chirpSpreadMs)

    /** A mean cannot correct a route whose delay moves between or within sessions. */
    fun isStable(maxSpreadMs: Double) = sampleRate > 0 && latencyFrames.isFinite() && latencyFrames >= 0 &&
        sessionSpreadMs >= 0 && chirpSpreadMs >= 0 && spreadMs <= maxSpreadMs && xruns == 0
}

object LatencyCalibration {
    /** Built-in and wired routes are stable to well below a millisecond on the Pixel 9. */
    const val MAX_SPREAD_MS = 5.0
    /** Bluetooth varies by up to about 20 ms between stream starts; accepted for music playback. */
    const val MAX_BLUETOOTH_SPREAD_MS = 25.0
    const val SESSION_COUNT = 3
    private const val MIN_VALID_SESSIONS = 2

    fun maxSpreadMs(bluetooth: Boolean) = if (bluetooth) MAX_BLUETOOTH_SPREAD_MS else MAX_SPREAD_MS

    /**
     * Combines the valid sessions; null if too few succeeded, their sample rates disagree or a
     * latency is not a time. Whether the result is usable depends on the route, see [CalibrationResult.isStable].
     */
    fun aggregate(sessions: List<SessionMeasurement>, measuredAtMillis: Long): CalibrationResult? {
        val valid = sessions.filter { it.isValid }
        if (valid.size < MIN_VALID_SESSIONS) return null
        val sampleRate = valid.first().sampleRate
        if (valid.any { it.sampleRate != sampleRate } || sampleRate <= 0) return null
        val toMs = 1000.0 / sampleRate

        val latencies = valid.map { it.latencyFrames }
        val timestamped = valid.filter { it.timestampLatencyFrames != null }
        val timestampLatencies = timestamped.map { it.timestampLatencyFrames!! }
        val deviations = timestamped.map { it.latencyFrames - it.timestampLatencyFrames!! }

        if (latencies.any { !it.isFinite() || it < 0 }) return null

        return CalibrationResult(
            latencyFrames = median(latencies),
            sampleRate = sampleRate,
            sessionSpreadMs = spread(latencies) * toMs,
            chirpSpreadMs = valid.maxOf { it.chirpSpreadFrames } * toMs,
            timestampLatencyMs = timestampLatencies.takeIf { it.isNotEmpty() }?.let { median(it) * toMs },
            timestampDeviationSpreadMs = deviations.takeIf { it.size >= MIN_VALID_SESSIONS }?.let { spread(it) * toMs },
            minSnrDb = valid.minOf { it.minSnrDb },
            sessions = valid.size,
            xruns = valid.sumOf { it.xruns },
            mmap = valid.all { it.outputMMap && it.inputMMap },
            measuredAtMillis = measuredAtMillis
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private fun spread(values: List<Double>) = values.max() - values.min()
}
