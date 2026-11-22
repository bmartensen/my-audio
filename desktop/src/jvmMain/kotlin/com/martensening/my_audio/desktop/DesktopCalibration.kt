package com.martensening.my_audio.desktop

import com.martensening.my_audio.audio.AudioSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.martensening.my_audio.audio.LatencyCalibration
import com.martensening.my_audio.audio.InputChannel
import com.martensening.my_audio.audio.NativeAudio
import com.martensening.my_audio.audio.SessionMeasurement
import com.martensening.my_audio.platform.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.roundToLong

/** A measured round trip of one device pair. */
data class DesktopCalibrationValue(val latencyFrames: Double, val spreadMs: Double, val measuredAtMillis: Long) {
    val latencyMs get() = latencyFrames * 1000.0 / NativeAudio.SAMPLE_RATE
}

/**
 * Calibrations per device pair in `calibration.json`, like on Android: every new combination of
 * playback device and microphone is measured before its first take.
 */
class DesktopCalibrationStore(root: File) {
    private val file = File(root, "calibration.json")

    private fun readAll(): Map<String, JsonObject> = runCatching {
        Json.parseToJsonElement(file.readText()).jsonObject.mapValues { it.value.jsonObject }
    }.getOrDefault(emptyMap())

    private fun writeAll(entries: Map<String, JsonObject>) = file.writeText(JsonObject(entries).toString())

    /** The pair's key for the current buffer choice: each buffer changes the round trip, so it is calibrated alone. */
    private fun keyOf(route: DesktopRoute) = route.key + AudioSettings.currentBuffer.calibrationSuffix

    fun load(route: DesktopRoute): DesktopCalibrationValue? = readAll()[keyOf(route)]?.let { json ->
        runCatching {
            DesktopCalibrationValue(
                latencyFrames = json.getValue("latencyFrames").jsonPrimitive.double,
                spreadMs = json.getValue("spreadMs").jsonPrimitive.double,
                measuredAtMillis = json.getValue("measuredAtMillis").jsonPrimitive.long
            )
        }.getOrNull()
    }

    fun latencyFrames(route: DesktopRoute): Long? = load(route)?.latencyFrames?.roundToLong()

    fun save(route: DesktopRoute, value: DesktopCalibrationValue) = writeAll(readAll() + (keyOf(route) to buildJsonObject {
        put("output", route.output)
        put("input", route.input)
        put("latencyFrames", value.latencyFrames)
        put("spreadMs", value.spreadMs)
        put("measuredAtMillis", value.measuredAtMillis)
    }))

    fun invalidate(route: DesktopRoute) = writeAll(readAll() - keyOf(route))
}

/** Runs the same three sessions and stability check as the Android calibration for the current devices. */
class DesktopCalibration(
    private val store: DesktopCalibrationStore,
    private val devices: DesktopDevices,
    private val scope: CoroutineScope
) {
    enum class Failure { Unstable, NoSignal, Audio, RouteChanged }

    var saved by mutableStateOf(store.load(devices.route()))
        private set
    /** 1-based session while measuring, else null. */
    var runningSession by mutableStateOf<Int?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    /** Spread of a rejected measurement, to show why it failed. */
    var rejectedSpreadMs by mutableStateOf<Double?>(null)
        private set

    /** After a device change: show the calibration of the new pair, if any. */
    fun refresh() {
        if (runningSession != null) return
        saved = store.load(devices.route())
        failure = null
        rejectedSpreadMs = null
    }

    fun start() {
        if (runningSession != null) return
        val route = devices.route()
        // A failed remeasurement must not leave an old, possibly wrong correction active.
        store.invalidate(route)
        saved = null
        failure = null
        rejectedSpreadMs = null
        runningSession = 1
        scope.launch {
            try {
                val sessions = mutableListOf<SessionMeasurement>()
                for (index in 1..LatencyCalibration.SESSION_COUNT) {
                    runningSession = index
                    val session = withContext(Dispatchers.Default) {
                        SessionMeasurement.fromNative(NativeAudio.runCalibrationSession(0, 0, false, false, InputChannel.DEVICE_CODE))
                    }
                    Log.i(TAG, "Calibration session $index: $session")
                    if (!route.samePorts(devices.route())) {
                        failure = Failure.RouteChanged
                        return@launch
                    }
                    sessions += session
                    delay(SESSION_PAUSE_MILLIS)
                }
                val result = LatencyCalibration.aggregate(sessions, System.currentTimeMillis())
                Log.i(TAG, "Calibration result: $result")
                when {
                    result == null -> failure = if (sessions.any { it.status == SessionMeasurement.STATUS_NO_SIGNAL })
                        Failure.NoSignal else Failure.Audio
                    result.isStable(LatencyCalibration.maxSpreadMs(bluetooth = false)) -> {
                        val value = DesktopCalibrationValue(result.latencyFrames, result.sessionSpreadMs, result.measuredAtMillis)
                        store.save(route, value)
                        saved = value
                    }
                    else -> {
                        failure = Failure.Unstable
                        rejectedSpreadMs = result.sessionSpreadMs
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Calibration failed", error)
                failure = Failure.Audio
            } finally {
                runningSession = null
            }
        }
    }

    private companion object {
        const val TAG = "MyAudioCalibration"
        const val SESSION_PAUSE_MILLIS = 300L
    }
}
