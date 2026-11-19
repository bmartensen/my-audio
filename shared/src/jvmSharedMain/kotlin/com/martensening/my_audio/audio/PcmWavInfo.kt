package com.martensening.my_audio.audio

import java.io.File
import java.io.RandomAccessFile

/** PCM16 mono/stereo layout; counts audio frames rather than interleaved channel samples. */
data class PcmWavInfo(val channels: Int, val sampleRate: Int, val dataOffset: Long, val frames: Long) {
    companion object {
        fun read(file: File): PcmWavInfo = RandomAccessFile(file, "r").use { input ->
            fun u16() = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
            fun u32() = java.lang.Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            fun tag() = ByteArray(4).also(input::readFully).decodeToString()
            require(tag() == "RIFF")
            u32()
            require(tag() == "WAVE")
            var channels = 0
            var rate = 0
            while (input.filePointer + 8 <= input.length()) {
                val type = tag()
                val bytes = u32()
                val start = input.filePointer
                if (type == "fmt ") {
                    require(bytes >= 16 && u16() == 1)
                    channels = u16()
                    rate = u32().toInt()
                    u32()
                    val align = u16()
                    require(channels in 1..2 && rate > 0 && align == channels * 2 && u16() == 16)
                } else if (type == "data") {
                    require(channels in 1..2)
                    val available = minOf(bytes, input.length() - start)
                    return@use PcmWavInfo(channels, rate, start, available / (2 * channels))
                }
                input.seek(start + bytes + (bytes and 1))
            }
            error("Missing PCM data")
        }
    }
}
