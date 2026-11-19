package com.martensening.my_audio.audio

import android.media.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun resample(input: FloatArray, inRate: Int, outRate: Int, chunk: Int = 1000): FloatArray {
        val output = ArrayList<Float>()
        val resampler = Resampler(inRate, outRate)
        var start = 0
        while (start < input.size) {
            val count = minOf(chunk, input.size - start)
            resampler.push(input.copyOfRange(start, start + count), count) { output += it }
            start += count
        }
        resampler.finish { output += it }
        return output.toFloatArray()
    }

    private fun sine(frequency: Double, rate: Int, frames: Int, amplitude: Double = 0.5) =
        FloatArray(frames) { (amplitude * sin(2 * PI * frequency * it / rate)).toFloat() }

    /** RMS of [signal] minus a best-fit sine at [frequency] (projection on sin/cos). */
    private fun residualRms(signal: FloatArray, frequency: Double, rate: Int, from: Int, to: Int): Pair<Double, Double> {
        var s = 0.0; var c = 0.0
        for (i in from until to) {
            s += signal[i] * sin(2 * PI * frequency * i / rate)
            c += signal[i] * kotlin.math.cos(2 * PI * frequency * i / rate)
        }
        val n = to - from
        s *= 2.0 / n; c *= 2.0 / n
        var residual = 0.0
        for (i in from until to) {
            val fit = s * sin(2 * PI * frequency * i / rate) + c * kotlin.math.cos(2 * PI * frequency * i / rate)
            residual += (signal[i] - fit) * (signal[i] - fit)
        }
        return sqrt(s * s + c * c) to sqrt(residual / n)
    }

    @Test
    fun keepsLengthAndToneWhenConvertingCdRateTo48k() {
        val input = sine(1000.0, 44_100, 44_100)
        val output = resample(input, 44_100, 48_000)

        assertEquals(48_000, output.size)
        // Away from the edges the tone keeps its amplitude and nothing else is added.
        val (amplitude, residual) = residualRms(output, 1000.0, 48_000, 2_000, 46_000)
        assertEquals(0.5, amplitude, 0.005)
        assertTrue("residual $residual", residual < 0.5 * 1e-3)
    }

    @Test
    fun chunkSizeDoesNotChangeTheResult() {
        val input = sine(440.0, 22_050, 10_000)
        assertArrayEquals(resample(input, 22_050, 48_000, chunk = 10_000), resample(input, 22_050, 48_000, chunk = 37), 1e-6f)
    }

    @Test
    fun downsamplingRemovesToneAboveTheNewNyquist() {
        // 30 kHz cannot exist at 48 kHz; it must not fold back as an 18 kHz alias.
        val input = sine(30_000.0, 96_000, 96_000)
        val output = resample(input, 96_000, 48_000)
        assertEquals(48_000, output.size)
        val (alias, _) = residualRms(output, 18_000.0, 48_000, 2_000, 46_000)
        assertTrue("alias $alias", alias < 0.5 * 1e-3)
    }

    @Test
    fun sameRatePassesSamplesThrough() {
        val input = sine(1000.0, 48_000, 5_000)
        assertArrayEquals(input, resample(input, 48_000, 48_000), 0f)
    }

    @Test
    fun preservesChannelsOfInterleavedPcm() {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        bytes.putShort(16384).putShort(0).putShort(-32768).putShort(-32768).flip()
        val (mono, frames) = AudioImport.readPcm(bytes, channels = 2, encoding = AudioFormat.ENCODING_PCM_16BIT, reuse = FloatArray(0))
        assertEquals(2, frames)
        assertArrayEquals(floatArrayOf(0.5f, 0f, -1f, -1f), mono, 1e-6f)
        assertEquals(-1f, mono[2], 1e-6f)

        val floats = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        floats.putFloat(0.5f).putFloat(-0.25f).flip()
        val (single, count) = AudioImport.readPcm(floats, channels = 1, encoding = AudioFormat.ENCODING_PCM_FLOAT, reuse = FloatArray(16))
        assertEquals(2, count)
        assertEquals(0.5f, single[0], 0f)
        assertEquals(-0.25f, single[1], 0f)
    }

    @Test
    fun readsWavSampleFormats() {
        // 24-bit packed (little endian): +0.5 and -1.
        val packed = ByteBuffer.wrap(byteArrayOf(0, 0, 0x40, 0, 0, 0x80.toByte())).order(ByteOrder.LITTLE_ENDIAN)
        val (mono24, frames24) = AudioImport.readPcm(packed, channels = 1, encoding = 21, reuse = FloatArray(0))
        assertEquals(2, frames24)
        assertEquals(0.5f, mono24[0], 1e-6f)
        assertEquals(-1f, mono24[1], 1e-6f)

        val int32 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        int32.putInt(Int.MIN_VALUE).putInt(1 shl 30).flip()
        val (mono32, frames32) = AudioImport.readPcm(int32, channels = 2, encoding = 22, reuse = FloatArray(0))
        assertEquals(1, frames32)
        assertArrayEquals(floatArrayOf(-1f, 0.5f), mono32, 1e-6f)

        // 8 bit is unsigned with 128 as silence.
        val (mono8, frames8) = AudioImport.readPcm(ByteBuffer.wrap(byteArrayOf(0x80.toByte(), 0, 0xC0.toByte())),
            channels = 1, encoding = AudioFormat.ENCODING_PCM_8BIT, reuse = FloatArray(0))
        assertEquals(3, frames8)
        assertEquals(0f, mono8[0], 0f)
        assertEquals(-1f, mono8[1], 0f)
        assertEquals(0.5f, mono8[2], 0f)
    }

    @Test
    fun writesTheEngineWavLayout() {
        val file = folder.newFile("1.wav")
        PcmWavWriter(file, 48_000).use { writer ->
            writer.write(0.5f)
            writer.write(-2f)   // clipped
            writer.write(0f)
        }
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44 + 6, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(36 + 6, header.getInt(4))
        assertEquals("WAVEfmt ", String(bytes, 8, 8))
        assertEquals(1, header.getShort(20).toInt())        // PCM
        assertEquals(1, header.getShort(22).toInt())        // mono
        assertEquals(48_000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(6, header.getInt(40))
        assertEquals(16384, header.getShort(44).toInt(), 1)
        assertEquals(-32767, header.getShort(46).toInt())
        assertEquals(0, header.getShort(48).toInt())
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("$actual not within $tolerance of $expected", abs(expected - actual) <= tolerance)
}
