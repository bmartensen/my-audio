package com.martensening.my_audio.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class GuitarTuningTest {
    private fun signal(frequency: Double, harmonics: Boolean = false, detune: Double = 0.0): FloatArray {
        val actual = frequency * 2.0.pow(detune / 1200)
        return FloatArray(2048) { i ->
            val phase = 2 * PI * actual * i / 16000
            (if (harmonics) 0.08 * sin(phase) + 0.3 * sin(2 * phase) + 0.16 * sin(3 * phase)
            else 0.4 * sin(phase)).toFloat()
        }
    }

    @Test fun standardStringsMatchConcertPitch() {
        val expected = listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)
        expected.forEachIndexed { index, hz ->
            assertEquals(hz, GuitarTuning.frequency(index), 0.0001)
            assertEquals(index, GuitarTuning.nearestString(hz))
        }
    }

    @Test fun detectsAllStringsAndSignedDetuningWithinTwoCents() {
        val detector = PitchDetector()
        for (string in 0..5) for (offset in listOf(-40.0, 0.0, 35.0)) {
            val detected = detector.detect(signal(GuitarTuning.frequency(string), detune = offset))!!
            assertEquals(offset, GuitarTuning.cents(detected, string), 2.0)
            assertEquals(string, GuitarTuning.nearestString(detected))
        }
    }

    @Test fun strongerOvertonesDoNotReplaceTheFundamental() {
        val detector = PitchDetector()
        for (string in 0..5) {
            val detected = detector.detect(signal(GuitarTuning.frequency(string), harmonics = true))!!
            assertEquals(0.0, GuitarTuning.cents(detected, string), 2.0)
        }
    }

    @Test fun rejectsSilenceDcNoiseAndClipping() {
        val detector = PitchDetector()
        val random = Random(42)
        assertNull(detector.detect(FloatArray(2048)))
        assertNull(detector.detect(FloatArray(2048) { 0.4f }))
        assertNull(detector.detect(FloatArray(2048) { random.nextFloat() * 0.4f - 0.2f }))
        assertNull(detector.detect(FloatArray(2048) { if (it % 100 < 50) 1f else -1f }))
        assertNull(detector.detect(FloatArray(2048) { Float.NaN }))
    }

    @Test fun noisyDecayingPluckedStringWithDcIsStillDetected() {
        val random = Random(7)
        val samples = signal(110.0, harmonics = true)
        samples.indices.forEach { i -> samples[i] = samples[i] * (1f - i / 3000f) + 0.1f + (random.nextFloat() - 0.5f) * 0.01f }
        assertEquals(110.0, PitchDetector().detect(samples)!!, 0.15)
    }

    @Test fun smoothingRejectsOneJumpAndClearsOnSilence() {
        val smoother = PitchSmoother()
        assertNull(smoother.update(110.0))
        assertNull(smoother.update(110.2))
        assertEquals(110.0, smoother.update(110.0)!!, 0.01)
        assertEquals(110.0, smoother.update(220.0)!!, 0.01)
        assertNotNull(smoother.update(null))
        assertNotNull(smoother.update(null))
        assertNull(smoother.update(null))
        assertNull(smoother.update(110.0))
    }

    @Test fun largeDetuningRemainsAccurateWithoutSnappingToATarget() {
        for (string in 0..5) for (offset in listOf(-250.0, -150.0, 150.0, 250.0)) {
            val detected = PitchDetector().detect(signal(GuitarTuning.frequency(string), harmonics = true, detune = offset))!!
            assertEquals(offset, GuitarTuning.cents(detected, string), 2.0)
        }
    }

    @Test fun unstableOctavesClearAndAConfirmedNewNoteIsAccepted() {
        val smoother = PitchSmoother()
        repeat(3) { smoother.update(110.0) }
        repeat(8) { smoother.update(if (it % 2 == 0) 220.0 else 110.0) }
        assertNull(smoother.update(220.0))
        repeat(3) { smoother.update(146.8324) }
        assertEquals(146.8324, smoother.update(146.8324)!!, 0.01)
    }

    @Test fun fineJitterIsReducedButATuningChangeStillConverges() {
        val smoother = PitchSmoother()
        repeat(3) { smoother.update(110.0) }
        val readings = (0 until 30).map { frame ->
            GuitarTuning.cents(smoother.update(110 * 2.0.pow((if (frame % 2 == 0) 3.0 else -3.0) / 1200))!!, 1)
        }
        assertTrue(readings.max() - readings.min() < 2.0)
        repeat(15) { smoother.update(110 * 2.0.pow(30.0 / 1200)) }
        assertEquals(30.0, GuitarTuning.cents(smoother.update(110 * 2.0.pow(30.0 / 1200))!!, 1), 1.0)
    }

    @Test fun automaticStringSelectionHasHysteresis() {
        val midpoint = kotlin.math.sqrt(GuitarTuning.frequency(0) * GuitarTuning.frequency(1))
        assertEquals(0, GuitarTuning.nearestString(midpoint * 2.0.pow(5.0 / 1200), 0))
        assertEquals(1, GuitarTuning.nearestString(midpoint * 2.0.pow(20.0 / 1200), 0))
    }

    @Test fun quietStringsAndBroadbandPickNoiseStillYieldTheFundamental() {
        for (string in 0..5) {
            val random = Random(17 + string)
            val samples = signal(GuitarTuning.frequency(string), harmonics = true)
            samples.indices.forEach { i -> samples[i] = samples[i] * 0.003f + (random.nextFloat() - 0.5f) * 0.001f }
            val result = PitchDetector().detect(samples)
            assertNotNull("quiet string $string", result)
            assertEquals(0.0, GuitarTuning.cents(result!!, string), 4.0)
        }
    }

    @Test fun intermittentFragmentsCannotKeepAnOldReadingAlive() {
        val smoother = PitchSmoother()
        repeat(3) { smoother.update(110.0) }
        repeat(8) { smoother.update(if (it % 2 == 0) null else 110.0) }
        assertNull(smoother.update(110.0))
    }

    @Test fun tracksADecayingStringDownToQuietLevels() {
        val detector = PitchDetector()
        val smoother = PitchSmoother()
        val random = Random(2026)
        var accepted = 0
        // Four seconds, with a falling amplitude and an initially slightly sharp attack.
        for (frame in 0 until 64) {
            val t = frame * 0.064
            val hz = 110.0 * 2.0.pow(8.0 * kotlin.math.exp(-t * 5) / 1200)
            val amplitude = 0.04 * kotlin.math.exp(-t / 1.2)
            val samples = FloatArray(2048) { i ->
                val phase = 2 * PI * hz * (t + i / 16000.0)
                (amplitude * (sin(phase) + 0.6 * sin(2 * phase) + 0.25 * sin(3 * phase)) +
                    (random.nextDouble() - 0.5) * 0.0002).toFloat()
            }
            val reading = smoother.update(detector.detect(samples))
            if (frame >= 5 && reading != null) {
                accepted++
                assertEquals(0.0, GuitarTuning.cents(reading, 1), 5.0)
            }
        }
        assertTrue("Should follow the tail, not just its attack: $accepted", accepted >= 56)
    }
}
