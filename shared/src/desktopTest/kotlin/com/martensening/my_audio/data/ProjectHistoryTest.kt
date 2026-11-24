package com.martensening.my_audio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectHistoryTest {
    private fun state(name: String) = Project(id = 1, name = name, createdAtMillis = 1, sampleRate = 48_000)

    @Test
    fun undoAndRedoWalkThroughTheStates() {
        val history = ProjectHistory()
        history.record(state("a"))
        history.record(state("b"))
        assertEquals(state("b"), history.undo(state("c")))
        assertEquals(state("a"), history.undo(state("b")))
        assertNull(history.undo(state("a")))
        assertEquals(state("b"), history.redo(state("a")))
        assertEquals(state("c"), history.redo(state("b")))
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun aNewChangeDropsTheRedoStates() {
        val history = ProjectHistory()
        history.record(state("a"))
        history.undo(state("b"))
        assertTrue(history.canRedo)
        history.record(state("a"))
        assertFalse(history.canRedo)
    }

    @Test
    fun changesWithTheSameKeyFormOneStep() {
        val history = ProjectHistory()
        history.record(state("a"), key = "slider")
        history.record(state("b"), key = "slider")
        history.record(state("c"), key = "slider")
        assertEquals(state("a"), history.undo(state("d")))
        assertFalse(history.canUndo)
        // After an undo the same key starts a new step.
        history.record(state("a"), key = "slider")
        assertTrue(history.canUndo)
    }

    @Test
    fun theOldestStatesFallOutAtTheLimit() {
        val history = ProjectHistory(limit = 2)
        history.record(state("a"))
        history.record(state("b"))
        history.record(state("c"))
        assertEquals(state("c"), history.undo(state("d")))
        assertEquals(state("b"), history.undo(state("c")))
        assertNull(history.undo(state("b")))
    }

    @Test
    fun timingMovesTheTakeAndItsEnvelopeButKeepsTheStart() {
        // A take started at the marker 96 000 with a 4-beat count-in of 96 000 and 1 000 frames latency.
        val take = Track(
            id = 1, name = "Take", fileName = "1.wav", offsetFrames = 96_000 - 96_000 - 1_000, lengthFrames = 480_000,
            createdAtMillis = 1, countInFrames = 96_000, latencyFrames = 1_000,
            volumeEnvelope = listOf(VolumePoint(100_000, 0.5f))
        )
        assertEquals(96_000L, take.startFrames)
        val shifted = take.withTiming(shift = 480)
        assertEquals(-1_000L + 480L, shifted.offsetFrames)
        assertEquals(480L, shifted.shiftFrames)
        assertEquals(96_000L, shifted.startFrames)
        assertEquals(100_480L, shifted.volumeEnvelope.single().frame)
        assertEquals(take, shifted.withTiming(shift = 0))
    }

    @Test
    fun projectsBefore011DeriveTheLatency() {
        // A calibrated take of 0.10: count-in and latency lie before frame 0.
        val take = Track(id = 1, name = "T", fileName = "1.wav", offsetFrames = -97_020, lengthFrames = 10,
            createdAtMillis = 1, countInFrames = 96_000)
        assertEquals(1_020L, take.latency)
        assertEquals(0L, take.startFrames)
        // An imported track (SoundCraft position) has no latency and starts at its offset.
        val imported = take.copy(offsetFrames = 48_000, countInFrames = 0)
        assertEquals(0L, imported.latency)
        assertEquals(48_000L, imported.startFrames)
        // An uncalibrated take gets its latency entered later; it then moves earlier.
        val uncalibrated = take.copy(offsetFrames = -96_000, latencyCalibrated = false)
        assertTrue(uncalibrated.needsLatency)
        val entered = uncalibrated.withTiming(latency = 1_000)
        assertEquals(-97_000L, entered.offsetFrames)
        assertFalse(entered.needsLatency)
    }
}
