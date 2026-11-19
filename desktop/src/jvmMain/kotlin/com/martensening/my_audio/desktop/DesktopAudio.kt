package com.martensening.my_audio.desktop

import com.martensening.my_audio.audio.ImportedAudioWriter
import com.martensening.my_audio.audio.OggOpusWriter
import com.martensening.my_audio.audio.PitchDetector
import com.martensening.my_audio.audio.PitchSmoother
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.martensening.my_audio.platform.loadNativeEngine
import com.martensening.my_audio.ui.ImportSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Desktop-only JNI functions of the native library (desktop/native). */
object DesktopNative {
    init {
        loadNativeEngine()
    }

    /** Interleaved mono/stereo samples of an MP3, WAV, FLAC or Ogg file; [info] receives sample rate and channels. Null if unreadable. */
    external fun decodeAudio(path: String, info: IntArray): FloatArray?

    /** Names of the connected playback ([capture] false) or recording devices. */
    external fun deviceNames(capture: Boolean): Array<String>

    /** Devices the engine and the calibration open from now on; "" = system default. */
    external fun selectDevices(playback: String, capture: String)

    /** Opus packets (20 ms) of interleaved 48 kHz [samples]; [preSkip] receives the encoder delay. Null on failure. */
    external fun encodeOpus(samples: FloatArray, channels: Int, bitrate: Int, preSkip: IntArray): Array<ByteArray>?

    /** Opens the selected microphone for the tuner; returns its name, or null if it cannot be opened. */
    external fun startCapture(sampleRate: Int): String?

    /** Copies waiting tuner samples into [buffer]; returns the count, or -1 if the device was lost. */
    external fun readCapture(buffer: FloatArray): Int

    external fun stopCapture()
}

/** The guitar tuner input on the desktop, with the same analysis as TunerCapture on Android. */
object DesktopTuner {
    private const val SAMPLE_RATE = 16_000

    suspend fun run(onReady: (String) -> Unit, onPitch: (Double?, Float, Boolean) -> Unit) = withContext(Dispatchers.Default) {
        val name = DesktopNative.startCapture(SAMPLE_RATE) ?: throw IllegalStateException("Cannot open the microphone")
        try {
            onReady(name)
            val block = FloatArray(512)
            val window = FloatArray(2048)
            val detector = PitchDetector(SAMPLE_RATE)
            val smoother = PitchSmoother()
            var filled = 0
            var sinceAnalysis = 0
            var lastSamples = System.nanoTime()
            while (isActive) {
                // Nonblocking reads keep cancellation prompt even if the device stops delivering audio.
                val count = DesktopNative.readCapture(block)
                check(count >= 0) { "Microphone lost" }
                val now = System.nanoTime()
                check(now - lastSamples < 2_000_000_000L) { "Microphone stopped delivering samples" }
                if (count == 0) {
                    delay(8)
                    continue
                }
                lastSamples = now
                if (filled + count > window.size) {
                    val retained = window.size - count
                    window.copyInto(window, 0, filled - retained, filled)
                    filled = retained
                }
                block.copyInto(window, filled, 0, count)
                filled += count
                sinceAnalysis += count
                if (filled == window.size && sinceAnalysis >= 1024) {
                    sinceAnalysis = 0
                    val raw = detector.detect(window)
                    val pitch = smoother.update(raw)
                    var peak = 0f
                    for (i in window.size - 1024 until window.size) peak = maxOf(peak, kotlin.math.abs(window[i]))
                    onPitch(pitch, peak, raw != null)
                }
            }
        } finally {
            DesktopNative.stopCapture()
        }
    }
}

/** "Share mix" on the desktop: libopus packets in the same Ogg container as on Android (WhatsApp-compatible). */
object DesktopOggExporter {
    private const val BIT_RATE = 160_000

    fun write(samples: FloatArray, sampleRate: Int, channels: Int, target: File) {
        require(sampleRate == 48_000) { "Opus runs at 48 kHz" }
        val preSkip = IntArray(1)
        val packets = checkNotNull(DesktopNative.encodeOpus(samples, channels, BIT_RATE, preSkip)) { "Opus encoding failed" }
        target.outputStream().buffered().use { out ->
            val writer = OggOpusWriter(out, channels, preSkip[0], totalSamples48k = samples.size / channels.toLong())
            packets.forEach(writer::writePacket)
            writer.finish()
        }
    }
}

/** Decodes audio files like AudioImport on Android: preserved mono/stereo channels, sinc resampling, 16-bit WAV. */
object DesktopAudioImport {
    /** Extensions the desktop decoder reads (Ogg Opus with its own reader, the rest through miniaudio). */
    val EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "oga", "opus")

    class UnsupportedAudioException(message: String) : Exception(message)

    suspend fun decodeToWav(source: File, target: File, sampleRate: Int): Long = withContext(Dispatchers.Default) {
        val info = IntArray(2)
        val samples = DesktopNative.decodeAudio(source.absolutePath, info)
            ?: throw UnsupportedAudioException("Cannot decode ${source.name}")
        ImportedAudioWriter(target, info[0], info[1], sampleRate).use { writer ->
            writer.push(samples)
            writer.finish()
            if (writer.frames == 0L) throw UnsupportedAudioException("No audio in ${source.name}")
            writer.frames
        }
    }
}

/** An audio file chosen in the desktop file dialog. */
class FileImportSource(private val file: File) : ImportSource {
    override val displayName: String? get() = file.nameWithoutExtension.trim().ifEmpty { null }

    override suspend fun decodeTo(target: File, sampleRate: Int) = DesktopAudioImport.decodeToWav(file, target, sampleRate)
}
