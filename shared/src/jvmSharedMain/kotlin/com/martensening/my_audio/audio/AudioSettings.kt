package com.martensening.my_audio.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Buffer of every duplex stream (calibration, takes, live monitoring): smaller means less monitoring
 * latency but a higher risk of dropouts. [code] is passed to the native engine: device periods (Android
 * bursts, desktop periods of 128 frames), 0 = the system's low-latency default. A stored code this version
 * does not offer falls back to Automatic.
 */
enum class AudioBuffer(val code: Int) {
    // A medium step (2 periods) is left out: it is about what Automatic already chooses on Android.
    Automatic(0), Short(1), Long(4);

    /**
     * Calibrations are stored per buffer, because the round trip changes with it; the default keeps the keys
     * calibrations had before this setting existed.
     */
    val calibrationSuffix get() = if (this == Automatic) "" else "|buffer=$code"

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code } ?: Automatic
    }
}

/** Device-wide audio settings in `audio.json` next to the projects; not part of any project or backup. */
class AudioSettings private constructor(private val file: File) {
    private val _buffer = MutableStateFlow(read())
    val buffer: StateFlow<AudioBuffer> = _buffer.asStateFlow()

    init {
        NativeAudio.setBufferSetting(_buffer.value.code)
    }

    fun setBuffer(value: AudioBuffer) {
        _buffer.value = value
        NativeAudio.setBufferSetting(value.code)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(buildJsonObject { put("buffer", value.code) }.toString())
        }
    }

    private fun read(): AudioBuffer = runCatching {
        AudioBuffer.of(Json.parseToJsonElement(file.readText()).jsonObject["buffer"]?.jsonPrimitive?.intOrNull)
    }.getOrDefault(AudioBuffer.Automatic)

    companion object {
        @Volatile
        private var instance: AudioSettings? = null

        /** The app-wide settings below [root]; also hands the buffer choice to the native engine. */
        fun forRoot(root: File): AudioSettings =
            instance ?: synchronized(this) { instance ?: AudioSettings(File(root, "audio.json")).also { instance = it } }

        /** The current buffer choice; the default before the settings are loaded. */
        val currentBuffer: AudioBuffer get() = instance?.buffer?.value ?: AudioBuffer.Automatic
    }
}
