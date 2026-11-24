package com.martensening.my_audio.audio

import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sin

/**
 * A take recorded from the start marker and moved by the user's offset, through the native take processing of
 * the desktop engine library: count-in and latency before the take's start stay silent, the take sounds from
 * its start, and the offset moves it.
 */
class StartMarkerTakeTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun useBuiltEngine() {
            val dir = File("../desktop/build/native").absoluteFile
            assumeTrue("myaudio.dll not built (:desktop:buildNative)", File(dir, System.mapLibraryName("myaudio")).isFile)
            System.setProperty("myaudio.native.dir", dir.path)
        }

        private const val RATE = NativeAudio.SAMPLE_RATE
        private const val MARKER = RATE * 2L
        private const val COUNT_IN = RATE / 2L
        private const val LATENCY = 960L
    }

    @get:Rule
    val folder = TemporaryFolder()

    /** A tone over the whole file, including the part recorded during count-in and latency. */
    private fun take(file: File, frames: Int) = PcmWavWriter(file, RATE).use { wav ->
        repeat(frames) { wav.write(0.5f * sin(2 * Math.PI * 440 * it / RATE).toFloat()) }
    }

    private fun stem(file: File): FloatArray {
        val info = PcmWavInfo.read(file)
        val bytes = ByteArray((info.frames * 2 * info.channels).toInt())
        RandomAccessFile(file, "r").use { it.seek(info.dataOffset); it.readFully(bytes) }
        return FloatArray(info.frames.toInt()) { i ->
            val index = i * info.channels * 2
            ((bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)).toShort() / 32768f
        }
    }

    private fun peak(samples: FloatArray, from: Long, to: Long) =
        (from.toInt() until to.toInt().coerceAtMost(samples.size)).maxOfOrNull { abs(samples[it]) } ?: 0f

    private fun stemFor(track: Track): FloatArray {
        val repository = ProjectRepository(folder.newFolder())
        var project = repository.create("Marker", RATE, 1)
        val file = repository.newTrackFile(project.id, 10)
        take(file, (COUNT_IN + LATENCY + RATE).toInt())
        project = repository.addTrack(project.id, track.copy(fileName = file.name))!!
        val target = File(folder.root, "stem-${track.shiftFrames}.wav")
        assertTrue(runBlocking { AudioEngine.exportStems(project, repository, listOf(target)) })
        return stem(target)
    }

    @Test
    fun countInAndLatencyBeforeTheMarkerStaySilentAndTheOffsetMovesTheTake() {
        val recorded = Track(
            id = 10, name = "Take", fileName = "", offsetFrames = MARKER - COUNT_IN - LATENCY,
            lengthFrames = COUNT_IN + LATENCY + RATE, createdAtMillis = 1, countInFrames = COUNT_IN, latencyFrames = LATENCY
        )
        val stem = stemFor(recorded)
        assertTrue("silent before the marker", peak(stem, 0, MARKER - 10) < 1e-3f)
        assertTrue("sounds from the marker", peak(stem, MARKER + 10, MARKER + RATE / 2) > 0.3f)

        val shift = RATE / 4L
        val moved = stemFor(recorded.withTiming(shift = shift))
        assertTrue("silent before the moved start", peak(moved, 0, MARKER + shift - 10) < 1e-3f)
        assertTrue("sounds from the moved start", peak(moved, MARKER + shift + 10, MARKER + shift + RATE / 2) > 0.3f)
    }
}
