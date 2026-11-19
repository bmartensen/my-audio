package com.martensening.my_audio.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Encodes interleaved float samples with [channels] channels as Ogg/Opus (the Ogg flavour
 * WhatsApp accepts) with the platform encoder (API 29). Opus always runs at 48 kHz internally.
 * The container is written by [OggOpusWriter], not `MediaMuxer`, whose Ogg output WhatsApp
 * rejects as broken.
 */
object OggExporter {
    private const val BIT_RATE = 160_000
    private const val TIMEOUT_US = 10_000L
    /** libopus lookahead at 48 kHz, used if the encoder does not report its delay. */
    private const val DEFAULT_PRE_SKIP = 312

    fun write(samples: FloatArray, sampleRate: Int, channels: Int, target: File) {
        val totalFrames = samples.size / channels
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val info = MediaCodec.BufferInfo()
        var nextFrame = 0
        var inputDone = false
        var outputDone = false
        target.outputStream().buffered().use { out ->
            var writer: OggOpusWriter? = null
            codec.start()
            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.nativeOrder())
                            buffer.clear()
                            val frames = min(buffer.remaining() / (2 * channels), totalFrames - nextFrame)
                            for (i in nextFrame * channels until (nextFrame + frames) * channels) {
                                buffer.putShort((samples[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
                            }
                            val presentationUs = nextFrame * 1_000_000L / sampleRate
                            nextFrame += frames
                            inputDone = nextFrame >= totalFrames
                            val flags = if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            codec.queueInputBuffer(index, 0, frames * 2 * channels, presentationUs, flags)
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index < 0) continue
                    val data = codec.getOutputBuffer(index)!!
                    // The codec config (Opus header) is replaced by our own OpusHead.
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0) {
                        val packet = ByteArray(info.size)
                        data.position(info.offset)
                        data.get(packet)
                        val ogg = writer ?: OggOpusWriter(
                            out, channels, preSkip(codec.outputFormat),
                            totalSamples48k = totalFrames * 48_000L / sampleRate,
                        ).also { writer = it }
                        ogg.writePacket(packet)
                    }
                    codec.releaseOutputBuffer(index, false)
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                }
            } finally {
                codec.stop()
                codec.release()
            }
            checkNotNull(writer) { "Opus encoder produced no audio" }.finish()
        }
    }

    /** Encoder delay in 48 kHz samples: from the OpusHead in csd-0, else the delay in ns in csd-1. */
    private fun preSkip(format: MediaFormat): Int {
        format.getByteBuffer("csd-0")?.order(ByteOrder.LITTLE_ENDIAN)?.takeIf { it.remaining() >= 19 }?.let { head ->
            val magic = ByteArray(8).also { head.duplicate().get(it) }
            if (String(magic) == "OpusHead") {
                return head.getShort(head.position() + 10).toInt() and 0xFFFF
            }
        }
        format.getByteBuffer("csd-1")?.order(ByteOrder.LITTLE_ENDIAN)?.takeIf { it.remaining() >= 8 }?.let {
            return (it.getLong(it.position()) * 48_000 / 1_000_000_000).toInt()
        }
        return DEFAULT_PRE_SKIP
    }
}
