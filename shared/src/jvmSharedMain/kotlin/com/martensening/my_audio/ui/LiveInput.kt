package com.martensening.my_audio.ui

import com.martensening.my_audio.audio.AudioEngine
import com.martensening.my_audio.audio.TransportRoute
import com.martensening.my_audio.platform.Log
import kotlinx.coroutines.delay

/** Why live monitoring on the current output may disappoint; the user is warned but may go on. */
enum class LiveWarning {
    /** The built-in loudspeaker next to the microphone feeds back. */
    Speaker,

    /** Bluetooth output adds roughly 150–300 ms. */
    Bluetooth
}

enum class LiveStart { Started, NoPermission, Failed }

/** The microphone → effect → output path of live audition; supplied by the platform. */
interface LiveInput {
    /** A reason to warn before monitoring on the current output, or null. */
    fun warning(): LiveWarning?

    /** Opens microphone and output; any running project playback has to be stopped first. */
    suspend fun start(): LiveStart

    suspend fun stop()
}

/** The platform's live input, set once at start-up; null where live audition is unavailable. */
object LiveInputs {
    @Volatile
    var current: LiveInput? = null
}

/**
 * Live audition over the platform's selected devices: the same routing and streams as a take, so what is
 * heard has the latency of recording. [hasPermission] checks microphone access, [warningOf] the output.
 */
class PlatformLiveInput(
    private val platform: ProjectPlatform,
    private val hasPermission: () -> Boolean,
    private val warningOf: (TransportRoute) -> LiveWarning?
) : LiveInput {
    private var running = false

    override fun warning(): LiveWarning? = warningOf(platform.route())

    override suspend fun start(): LiveStart {
        if (!hasPermission()) return LiveStart.NoPermission
        // A project playback stopped for the audition may still be closing its stream.
        repeat(STOP_WAIT_STEPS) { if (AudioEngine.isActive) delay(STOP_WAIT_MILLIS) }
        val route = platform.route()
        if (!route.ready || !platform.startRouting(route)) return LiveStart.Failed
        if (!AudioEngine.startMonitoring(route)) {
            Log.w(TAG, "Live monitoring could not start")
            platform.stopRouting()
            return LiveStart.Failed
        }
        running = true
        return LiveStart.Started
    }

    override suspend fun stop() {
        if (!running) return
        running = false
        AudioEngine.stop()
        AudioEngine.setMonitor(null)
        platform.stopRouting()
    }

    private companion object {
        const val TAG = "MyAudioLive"
        const val STOP_WAIT_STEPS = 50
        const val STOP_WAIT_MILLIS = 20L
    }
}
