package com.martensening.my_audio.audio

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * Writes interleaved mono/stereo PCM16 with the same 44-byte header as the native WavWriter.
 * Quantisation happens once, after decoding and resampling. Samples are clipped to ±1.
 */
class PcmWavWriter(private val file: File, private val sampleRate: Int, val channels: Int = 1) : Closeable {
    init { require(channels in 1..2) }
    private val output = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
    private var samples = 0L
    val frames get() = samples / channels

    init {
        output.write(header(sampleRate, 0, channels))
    }

    fun write(sample: Float) {
        val value = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt()
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
        samples++
    }

    private var bytes = ByteArray(0)

    /** Writes the first [count] interleaved samples of [block] at once, quantised exactly like [write]. */
    fun write(block: FloatArray, count: Int) {
        if (bytes.size < count * 2) bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val value = (block[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
            bytes[i * 2] = value.toByte()
            bytes[i * 2 + 1] = (value shr 8).toByte()
        }
        output.write(bytes, 0, count * 2)
        samples += count
    }

    override fun close() {
        output.close()
        check(samples % channels == 0L) { "Incomplete PCM frame" }
        RandomAccessFile(file, "rw").use { it.write(header(sampleRate, samples * 2, channels)) }
    }

    companion object {
        const val HEADER_BYTES = 44

        fun header(sampleRate: Int, dataBytes: Long, channels: Int = 1): ByteArray {
            val header = ByteArray(HEADER_BYTES)
            fun text(offset: Int, value: String) = value.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
            fun u32(offset: Int, value: Long) = repeat(4) { header[offset + it] = (value shr (8 * it)).toByte() }
            fun u16(offset: Int, value: Int) = repeat(2) { header[offset + it] = (value shr (8 * it)).toByte() }
            text(0, "RIFF"); u32(4, 36 + dataBytes)
            text(8, "WAVEfmt "); u32(16, 16)
            u16(20, 1)                                  // PCM
            u16(22, channels)
            u32(24, sampleRate.toLong())
            u32(28, sampleRate * 2L * channels)         // byte rate
            u16(32, 2 * channels)                       // block align
            u16(34, 16)                                 // bits per sample
            text(36, "data"); u32(40, dataBytes)
            return header
        }
    }
}
