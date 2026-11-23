package com.martensening.my_audio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Turns an MP3, Ogg (Vorbis/Opus) or WAV file into a take in the internal format: decoded with the
 * platform codecs, preserving mono/stereo channels in 16-bit WAV at the project rate.
 */
object AudioImport {
    /** Offered in the file picker; the decoder accepts whatever the platform can read. */
    val MIME_TYPES = arrayOf(
        "audio/mpeg", "audio/mp3", "audio/ogg", "application/ogg", "audio/x-ogg", "audio/opus", "audio/vorbis",
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"
    )

    class UnsupportedAudioException(message: String) : Exception(message)

    /** File name without extension for the new track, or null if the provider does not tell. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }

    /** Decodes [uri] into [target] (mono/stereo, 16 bit, [sampleRate]); returns the number of frames written. */
    suspend fun decodeToWav(context: Context, uri: Uri, target: File, sampleRate: Int): Long =
        decodeToWav(target, sampleRate) { it.setDataSource(context, uri, null) }

    /** Decodes the audio file [source] into [target]; see the [Uri] variant. */
    suspend fun decodeToWav(source: File, target: File, sampleRate: Int): Long =
        decodeToWav(target, sampleRate) { it.setDataSource(source.path) }

    private suspend fun decodeToWav(target: File, sampleRate: Int, open: (MediaExtractor) -> Unit): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: ImportedAudioWriter? = null
        try {
            open(extractor)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw UnsupportedAudioException("No audio track")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = runCatching { MediaCodec.createDecoderByType(mime) }
                .getOrElse { throw UnsupportedAudioException("No decoder for $mime") }
            // For compressed audio request float output, then inspect the actual output below.
            // For audio/raw this key also describes the INPUT: preserve the WAV extractor's encoding.
            if (mime != "audio/raw") format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            codec.configure(format, null, null, 0)
            codec.start()

            var outputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var pcm = FloatArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            run {
                while (true) {
                    coroutineContext.ensureActive()
                    // Fill every free input buffer without waiting, then wait only briefly for output while
                    // input remains: waiting 10 ms per packet made long imports take minutes.
                    while (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(0)
                        if (inputIndex < 0) break
                        val input = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    val outputIndex = codec.dequeueOutputBuffer(info, if (inputDone) TIMEOUT_US else SHORT_TIMEOUT_US)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val output = codec.outputFormat
                        outputRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        continue
                    }
                    if (outputIndex < 0) continue
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val (samples, frames) = readPcm(buffer.slice().order(ByteOrder.nativeOrder()), channels, encoding, pcm)
                        pcm = samples
                        val sink = writer ?: ImportedAudioWriter(target, outputRate, channels, sampleRate).also { writer = it }
                        check(sink.inputRate == outputRate && sink.channels == channels) { "Audio format changed mid-stream" }
                        sink.push(pcm, frames)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                val sink = writer ?: throw UnsupportedAudioException("No audio decoded")
                sink.finish()
                if (sink.frames == 0L) throw UnsupportedAudioException("No audio decoded")
                return sink.frames
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
            writer?.close()
        }
    }

    /**
     * Converts interleaved mono/stereo PCM to float without combining the channels.
     * Returns the interleaved array and the number of audio frames.
     */
    fun readPcm(buffer: ByteBuffer, channels: Int, encoding: Int, reuse: FloatArray): Pair<FloatArray, Int> {
        if (channels !in 1..2) throw UnsupportedAudioException("Channel count $channels")
        val channelCount = channels
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, PCM_32BIT -> 4
            PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> throw UnsupportedAudioException("PCM encoding $encoding")
        }
        val frames = buffer.remaining() / (bytesPerSample * channelCount)
        val samples = frames * channelCount
        val pcm = if (reuse.size >= samples) reuse else FloatArray(samples)
        val little = buffer.order() == ByteOrder.LITTLE_ENDIAN
        for (sample in 0 until samples) {
            pcm[sample] = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat()
                PCM_32BIT -> buffer.getInt() / 2147483648f
                PCM_24BIT_PACKED -> {
                    val a = buffer.get().toInt() and 0xFF
                    val b = buffer.get().toInt() and 0xFF
                    val c = buffer.get().toInt()
                    // Sign comes from the most significant byte.
                    (if (little) (c shl 16) or (b shl 8) or a else (a.toByte().toInt() shl 16) or (b shl 8) or (c and 0xFF)) / 8388608f
                }
                AudioFormat.ENCODING_PCM_16BIT -> buffer.getShort() / 32768f
                else -> ((buffer.get().toInt() and 0xFF) - 128) / 128f   // 8 bit is unsigned
            }
        }
        return pcm to frames
    }

    // AudioFormat.ENCODING_PCM_24BIT_PACKED / ENCODING_PCM_32BIT (API 31); a WAV decoder may report them.
    private const val PCM_24BIT_PACKED = 21
    private const val PCM_32BIT = 22
    private const val TIMEOUT_US = 10_000L
    private const val SHORT_TIMEOUT_US = 1_000L
}
