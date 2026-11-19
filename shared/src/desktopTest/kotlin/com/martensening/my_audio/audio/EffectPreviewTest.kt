package com.martensening.my_audio.audio

import com.martensening.my_audio.data.ChainEffect
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.data.ReverbEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** The native effect preview (`EffectPreview.cpp`), through the desktop engine library when it is built. */
class EffectPreviewTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun useBuiltEngine() {
            val dir = File("../desktop/build/native").absoluteFile
            assumeTrue("myaudio.dll not built (:desktop:buildNative)", File(dir, System.mapLibraryName("myaudio")).isFile)
            System.setProperty("myaudio.native.dir", dir.path)
        }

        private const val RATE = NativeAudio.SAMPLE_RATE
    }

    /** Interleaved stereo; [fill] gives the sample of a frame. */
    private fun clip(seconds: Double, fill: (Int) -> Float) = FloatArray((seconds * RATE).toInt() * 2) { fill(it / 2) }

    private fun rmsDb(loop: FloatArray, fromFrame: Int, toFrame: Int): Double {
        var sum = 0.0
        for (i in fromFrame * 2 until toFrame * 2) sum += loop[i] * loop[i]
        return 10 * log10(sum / ((toFrame - fromFrame) * 2) + 1e-20)
    }

    @Test
    fun loopKeepsTheExcerptLengthAndFoldsTheReverbTailOntoItsStart() {
        // A short burst at the very end: its reverb tail can only be heard if it wraps to the start.
        val frames = 2 * RATE
        val burst = clip(2.0) { frame -> if (frame > frames - RATE / 20) (sin(2 * PI * 440 * frame / RATE) * 0.5).toFloat() else 0f }
        assertTrue(NativeAudio.previewSamples(burst, 2))

        val dry = NativeAudio.previewRender(FloatArray(0))
        val wet = NativeAudio.previewRender(AudioEngine.encodeChain(listOf(
            EffectSlot(ChainEffect.Reverb(ReverbEffect().with(ReverbEffect.Preset.Hall)))
        )))
        assertEquals(burst.size, dry.size)
        assertEquals(burst.size, wet.size)
        val startDry = rmsDb(dry, RATE / 10, RATE / 2)
        val startWet = rmsDb(wet, RATE / 10, RATE / 2)
        assertTrue("dry start must be silent, was $startDry dB", startDry < -90)
        assertTrue("reverb tail must wrap to the start, was $startWet dB", startWet > -60)
        assertTrue("never clips", wet.all { abs(it) <= 0.99f })
    }

    @Test
    fun withAndWithoutALevelMatchedEffectSoundEquallyLoudAndRenderQuickly() {
        val music = clip(6.0) { frame ->
            val t = frame.toDouble() / RATE
            (0.2 * sin(2 * PI * 110 * t) + 0.1 * sin(2 * PI * 330 * t) * (0.5 + 0.5 * sin(2 * PI * 2 * t))).toFloat()
        }
        assertTrue(NativeAudio.previewSamples(music, 2))
        val drive = AirwindowsCatalog.find("Drive") ?: error("Drive missing from the catalog")
        val chain = listOf(EffectSlot(ChainEffect.Airwindows(drive.name, listOf(0.85f, 0.65f, 1f, 1f))))

        val without = NativeAudio.previewRender(AudioEngine.encodeChain(emptyList()))
        val started = System.nanoTime()
        val with = NativeAudio.previewRender(AudioEngine.encodeChain(chain))
        val millis = (System.nanoTime() - started) / 1_000_000
        println("6 s preview through Drive rendered in $millis ms")

        val difference = rmsDb(with, RATE, 5 * RATE) - rmsDb(without, RATE, 5 * RATE)
        assertTrue("A/B level difference $difference dB", abs(difference) < 1.5)
        // Listening level about -18 LUFS for a sine mix: RMS well above silence, below clipping.
        val level = rmsDb(without, RATE, 5 * RATE)
        assertTrue("listening level $level dB", level in -30.0..-10.0)
        assertTrue("faster than real time by far", millis < 1500)
        assertTrue(sqrt(with.sumOf { (it * it).toDouble() }) > 0)
    }
}
