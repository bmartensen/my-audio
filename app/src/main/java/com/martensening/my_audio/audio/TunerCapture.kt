package com.martensening.my_audio.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Foreground-only input. Cancellation releases capture, focus and SCO before transport is enabled. */
object TunerCapture {
    @SuppressLint("MissingPermission") // ProjectScreen requests RECORD_AUDIO before starting this session.
    suspend fun run(context: Context, onInterrupted: () -> Unit, onReady: (String) -> Unit,
                    onPitch: (Double?, Float, Boolean) -> Unit) = withContext(Dispatchers.Default) {
        val devices = AudioDevices(context)
        val route = devices.route()
        val endpoint = checkNotNull(route.input)
        val audio = context.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({ if (it < 0) onInterrupted() }, Handler(Looper.getMainLooper()))
            .build()
        var recorder: AudioRecord? = null
        try {
            check(audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            check(devices.startRouting(route))
            val input = checkNotNull(audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == endpoint.id })
            // A two-channel interface listens to the same input as the take (see InputChannel).
            val channel = route.inputChannel
            val channels = if (channel != null) 2 else 1
            val mask = if (channel != null) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val minimum = AudioRecord.getMinBufferSize(16_000, mask, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            val capture = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(16_000)
                    .setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minimum, 8192 * channels)).build()
            recorder = capture
            check(capture.state == AudioRecord.STATE_INITIALIZED && capture.setPreferredDevice(input))
            capture.startRecording()
            check(capture.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            onReady(endpoint.name)
            val block = ShortArray(512 * channels)
            val window = FloatArray(2048)
            val detector = PitchDetector()
            val smoother = PitchSmoother()
            var filled = 0
            var sinceAnalysis = 0
            var routeCheckFrames = 0
            var lastRouteCheck = 0L
            var lastSamples = SystemClock.elapsedRealtime()
            var lastLog = 0L
            while (currentCoroutineContext().isActive) {
                // Nonblocking reads keep cancellation prompt even if a device stops delivering audio.
                val samples = capture.read(block, 0, block.size, AudioRecord.READ_NON_BLOCKING)
                check(samples >= 0)
                val count = samples / channels
                val now = SystemClock.elapsedRealtime()
                check(now - lastSamples < 2000) { "Microphone stopped delivering samples" }
                if (now - lastRouteCheck >= 100) {
                    check(devices.route().input?.id == endpoint.id)
                    if (routeCheckFrames >= 2048) check(capture.routedDevice?.id == endpoint.id)
                    lastRouteCheck = now
                }
                if (count == 0) { delay(8); continue }
                lastSamples = now
                routeCheckFrames += count
                if (filled + count > window.size) {
                    val retained = window.size - count
                    window.copyInto(window, 0, filled - retained, filled)
                    filled = retained
                }
                for (i in 0 until count) {
                    window[filled + i] = when (channel) {
                        null, InputChannel.First -> block[i * channels].toInt()
                        InputChannel.Second -> block[i * channels + 1].toInt()
                        InputChannel.Both -> block[i * channels] + block[i * channels + 1]
                    } / 32768f
                }
                filled += count
                sinceAnalysis += count
                if (filled == window.size && sinceAnalysis >= 1024) {
                    sinceAnalysis = 0
                    val raw = detector.detect(window)
                    val pitch = smoother.update(raw)
                    var peak = 0f
                    for (i in window.size - 1024 until window.size) peak = maxOf(peak, kotlin.math.abs(window[i]))
                    onPitch(pitch, peak, raw != null)
                    if (now - lastLog >= 1000 && context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        Log.d("MyAudioTuner", "input=${endpoint.name} peak=$peak raw=$raw pitch=$pitch confidence=${detector.confidence}")
                        lastLog = now
                    }
                }
            }
        } finally {
            try {
                recorder?.let { capture ->
                    try { if (capture.recordingState == AudioRecord.RECORDSTATE_RECORDING) capture.stop() }
                    finally { capture.release() }
                }
            } finally {
                try { devices.stopRouting() } finally { audio.abandonAudioFocusRequest(focus) }
            }
        }
    }
}
