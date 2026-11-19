package com.martensening.my_audio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyCalibrationTest {
    private fun session(
        latencyFrames: Double,
        timestampLatencyFrames: Double? = latencyFrames - 48.0,
        status: Int = SessionMeasurement.STATUS_OK,
        sampleRate: Int = 48_000,
        inputMMap: Boolean = true
    ) = SessionMeasurement(
        status = status,
        sampleRate = sampleRate,
        validChirps = 6,
        latencyFrames = latencyFrames,
        chirpSpreadFrames = 2.4,
        minSnrDb = 30.0,
        timestampLatencyFrames = timestampLatencyFrames,
        xruns = 0,
        outputMMap = true,
        inputMMap = inputMMap,
        framesPerBurst = 96,
        outputBufferFrames = 192
    )

    @Test
    fun aggregatesMedianAndSpreads() {
        val result = LatencyCalibration.aggregate(
            listOf(session(1440.0), session(1536.0), session(1488.0, inputMMap = false)),
            measuredAtMillis = 0L
        )!!

        assertEquals(1488.0, result.latencyFrames, 1e-9)
        assertEquals(31.0, result.latencyMs, 1e-9)
        assertEquals(2.0, result.sessionSpreadMs, 1e-9)
        assertEquals(0.05, result.chirpSpreadMs, 1e-9)
        assertEquals(30.0, result.timestampLatencyMs!!, 1e-9)
        // A constant offset to the timestamps means they explain every session change.
        assertEquals(0.0, result.timestampDeviationSpreadMs!!, 1e-9)
        assertEquals(3, result.sessions)
        assertFalse(result.mmap)
    }

    @Test
    fun ignoresFailedSessions() {
        val result = LatencyCalibration.aggregate(
            listOf(session(1440.0), session(0.0, status = SessionMeasurement.STATUS_NO_SIGNAL), session(1440.0)),
            measuredAtMillis = 0L
        )!!

        assertEquals(1440.0, result.latencyFrames, 1e-9)
        assertEquals(2, result.sessions)
    }

    @Test
    fun omitsTimestampFiguresWithoutTimestamps() {
        val result = LatencyCalibration.aggregate(
            listOf(session(1440.0, timestampLatencyFrames = null), session(1440.0, timestampLatencyFrames = null)),
            measuredAtMillis = 0L
        )!!

        assertNull(result.timestampLatencyMs)
        assertNull(result.timestampDeviationSpreadMs)
    }

    @Test
    fun rejectsTooFewValidSessions() {
        val failed = session(0.0, status = SessionMeasurement.STATUS_NO_SIGNAL)
        assertNull(LatencyCalibration.aggregate(listOf(session(1440.0), failed, failed), measuredAtMillis = 0L))
    }

    @Test
    fun rejectsMixedSampleRates() {
        assertNull(
            LatencyCalibration.aggregate(
                listOf(session(1440.0), session(1323.0, sampleRate = 44_100)),
                measuredAtMillis = 0L
            )
        )
    }

    private fun stable(vararg sessions: SessionMeasurement, bluetooth: Boolean = false) =
        LatencyCalibration.aggregate(sessions.toList(), 0)!!.isStable(LatencyCalibration.maxSpreadMs(bluetooth))

    @Test
    fun rejectsDelayThatChangesBetweenSessions() {
        assertFalse(stable(session(1000.0), session(2300.0), bluetooth = true))
    }

    @Test
    fun rejectsDelayThatChangesWithinOneSession() {
        assertFalse(stable(session(1000.0), session(1000.0).copy(chirpSpreadFrames = 1300.0), bluetooth = true))
    }

    @Test
    fun rejectsAudioDropoutsAndInvalidLatency() {
        assertFalse(stable(session(1000.0), session(1000.0).copy(xruns = 1), bluetooth = true))
        for (bad in listOf(session(Double.NaN), session(Double.POSITIVE_INFINITY), session(-100.0))) {
            assertNull(LatencyCalibration.aggregate(listOf(session(1000.0), bad), 0))
        }
    }

    @Test
    fun bluetoothAcceptsTwentyFiveMillisecondsButNotTheNextSample() {
        assertTrue(stable(session(1000.0), session(2200.0), bluetooth = true))
        assertFalse(stable(session(1000.0), session(2201.0), bluetooth = true))
    }

    @Test
    fun wiredAndBuiltInRoutesAcceptFiveMilliseconds() {
        assertTrue(stable(session(1000.0), session(1240.0)))
        assertFalse(stable(session(1000.0), session(1241.0)))
    }
}
