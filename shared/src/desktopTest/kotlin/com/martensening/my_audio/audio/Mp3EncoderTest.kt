package com.martensening.my_audio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/** "Mix as MP3" (`Mp3Encoder.cpp`, LAME) through the desktop engine library when it is built. */
class Mp3EncoderTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun useBuiltEngine() {
            val dir = File("../desktop/build/native").absoluteFile
            assumeTrue("myaudio.dll not built (:desktop:buildNative)", File(dir, System.mapLibraryName("myaudio")).isFile)
            System.setProperty("myaudio.native.dir", dir.path)
        }

        private const val RATE = NativeAudio.SAMPLE_RATE
        private const val SAMPLES_PER_FRAME = 1152

        /** Offset of the Xing/Info tag in an MPEG-1 stereo frame: header plus 32 bytes of side information. */
        private const val XING_OFFSET = 4 + 32
    }

    private fun bigEndian(bytes: ByteArray, offset: Int, count: Int) =
        (0 until count).fold(0L) { value, i -> (value shl 8) or (bytes[offset + i].toLong() and 0xff) }

    @Test
    fun encodesAStereoMixWithATagThatGivesItsExactLength() {
        val frames = 3 * RATE + 777
        val mix = FloatArray(frames * 2) { i ->
            val frame = i / 2
            (sin(2 * PI * (if (i % 2 == 0) 440.0 else 660.0) * frame / RATE) * 0.5).toFloat()
        }
        val mp3 = NativeAudio.encodeMp3(mix, 2, RATE)
        assertNotNull(mp3)
        mp3!!

        // MPEG-1 Layer III frame header at 48 kHz, no ID3 tag in front.
        assertEquals(0xff, mp3[0].toInt() and 0xff)
        assertEquals(0xfa, mp3[1].toInt() and 0xfe)
        assertEquals(1, (mp3[2].toInt() shr 2) and 0x3)

        // VBR stream: the first frame carries the Xing tag with frame count and LAME's delay and padding.
        assertEquals("Xing", String(mp3, XING_OFFSET, 4, Charsets.US_ASCII))
        val flags = bigEndian(mp3, XING_OFFSET + 4, 4)
        assertTrue("frame count present", flags and 1L != 0L)
        val mp3Frames = bigEndian(mp3, XING_OFFSET + 8, 4)
        var lame = XING_OFFSET + 8
        if (flags and 1L != 0L) lame += 4
        if (flags and 2L != 0L) lame += 4
        if (flags and 4L != 0L) lame += 100
        if (flags and 8L != 0L) lame += 4
        assertEquals("LAME", String(mp3, lame, 4, Charsets.US_ASCII))
        val delayPadding = bigEndian(mp3, lame + 21, 3)
        val delay = (delayPadding shr 12).toInt()
        val padding = (delayPadding and 0xfff).toInt()
        assertEquals(frames.toLong(), mp3Frames * SAMPLES_PER_FRAME - delay - padding)

        // About 190 kbit/s for VBR quality 2; generous bounds for a pure two-tone signal.
        val kbps = mp3.size * 8.0 / (frames.toDouble() / RATE) / 1000
        assertTrue("bit rate $kbps kbit/s", kbps in 48.0..320.0)
    }

    @Test
    fun encodesMonoAndRejectsBrokenInput() {
        val mono = FloatArray(RATE) { (sin(2 * PI * 220 * it / RATE) * 0.3).toFloat() }
        val mp3 = NativeAudio.encodeMp3(mono, 1, RATE)
        assertNotNull(mp3)
        assertEquals(0xff, mp3!![0].toInt() and 0xff)
        assertEquals(null, NativeAudio.encodeMp3(FloatArray(3), 2, RATE))
    }
}
