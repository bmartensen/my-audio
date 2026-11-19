package com.martensening.my_audio.platform

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToInt

actual fun openPcmOutput(sampleRate: Int, channels: Int): PcmOutput? = runCatching {
    val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
    val line = AudioSystem.getSourceDataLine(format)
    // About 80 ms of buffer: short enough that a changed setting is heard at once.
    line.open(format, sampleRate / 12 * channels * 2)
    line.start()
    object : PcmOutput {
        private var bytes = ByteArray(0)

        override fun write(samples: FloatArray, count: Int) {
            if (bytes.size < count * 2) bytes = ByteArray(count * 2)
            for (i in 0 until count) {
                val value = (samples[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
                bytes[i * 2] = value.toByte()
                bytes[i * 2 + 1] = (value shr 8).toByte()
            }
            line.write(bytes, 0, count * 2)
        }

        override fun close() {
            line.stop()
            line.flush()
            line.close()
        }
    }
}.getOrNull()
