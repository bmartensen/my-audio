package com.martensening.my_audio.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.max

object Waveform {
    /** One waveform for either format: the maximum channel peak never cancels opposite phases. */
    fun peaks(file: File, bins: Int): FloatArray {
        require(bins > 0)
        val result = FloatArray(bins)
        val info = runCatching { PcmWavInfo.read(file) }.getOrNull() ?: return result
        val frames = info.frames
        if (frames == 0L) return result
        val framesPerBin = (frames + bins - 1) / bins
        val buffer = ByteArray(64 * 1024)
        var frame = 0L
        file.inputStream().buffered().use { input ->
            var skip = info.dataOffset
            while (skip > 0) {
                val skipped = input.skip(skip)
                if (skipped <= 0) return result
                skip -= skipped
            }
            val frameBytes = 2 * info.channels
            while (frame < frames) {
                val wanted = minOf(buffer.size.toLong(), (frames - frame) * frameBytes).toInt()
                var read = 0
                while (read < wanted) {
                    val n = input.read(buffer, read, wanted - read)
                    if (n < 0) break
                    read += n
                }
                if (read <= 0) break
                for (i in 0..read - frameBytes step frameBytes) {
                    val bin = (frame / framesPerBin).toInt().coerceAtMost(bins - 1)
                    for (channel in 0 until info.channels) {
                        val at = i + channel * 2
                        val sample = (buffer[at].toInt() and 0xFF) or (buffer[at + 1].toInt() shl 8)
                        result[bin] = max(result[bin], abs(sample.toShort().toInt()) / 32768f)
                    }
                    frame++
                }
            }
        }
        return result
    }
}
