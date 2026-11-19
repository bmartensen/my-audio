package com.martensening.my_audio.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

actual fun openPcmOutput(sampleRate: Int, channels: Int): PcmOutput? = runCatching {
    val mask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val minimum = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_FLOAT)
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(mask).build()
        )
        // About 80 ms: short enough that a changed setting is heard at once.
        .setBufferSizeInBytes(maxOf(minimum, sampleRate / 12 * channels * Float.SIZE_BYTES))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    track.play()
    object : PcmOutput {
        override fun write(samples: FloatArray, count: Int) {
            track.write(samples, 0, count, AudioTrack.WRITE_BLOCKING)
        }

        override fun close() {
            runCatching {
                track.pause()
                track.flush()
                track.stop()
            }
            track.release()
        }
    }
}.getOrNull()
