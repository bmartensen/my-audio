package com.martensening.my_audio.platform

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack

/** The output previews play on: the app's selected output device, set by the app at start-up. */
object PreviewOutput {
    @Volatile
    var device: () -> AudioDeviceInfo? = { null }
}

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
    // Like Play and Record: the device chosen in the settings, not just the system's default output.
    runCatching { PreviewOutput.device() }.getOrNull()?.let { track.setPreferredDevice(it) }
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
