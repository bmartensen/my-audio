package com.martensening.my_audio.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.martensening.my_audio.audio.AudioDeviceRoute
import com.martensening.my_audio.audio.AudioDevices
import com.martensening.my_audio.audio.CalibrationResult
import com.martensening.my_audio.audio.CalibrationStore
import com.martensening.my_audio.audio.LatencyCalibration
import com.martensening.my_audio.audio.InputChannel
import com.martensening.my_audio.audio.NativeAudio
import com.martensening.my_audio.audio.SessionMeasurement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CalibrationUiState(
    /** Selected playback device and microphone; the shown calibration belongs to this pair. */
    val route: AudioDeviceRoute = AudioDeviceRoute(null, null),
    val saved: CalibrationResult? = null,
    /** 1-based number of the running session, or null when idle. */
    val runningSession: Int? = null,
    val errorStatus: Int? = null,
    /** Values of a measurement that was evaluated but rejected, e.g. as unstable. */
    val rejected: CalibrationResult? = null
)

class CalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CalibrationStore(application)
    private val devices = AudioDevices(application)
    private val _state = MutableStateFlow(devices.route().let { CalibrationUiState(route = it, saved = store.load(it)) })
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

    /** Follows device changes; a failure stays visible only while the same ports are selected. */
    fun refreshRoute() {
        if (_state.value.runningSession != null) return
        val route = devices.route()
        _state.update {
            val same = it.route.samePorts(route)
            it.copy(
                route = route,
                saved = store.load(route),
                errorStatus = it.errorStatus.takeIf { same },
                rejected = it.rejected.takeIf { same }
            )
        }
    }

    fun startCalibration() {
        if (_state.value.runningSession != null) return
        val route = devices.route()
        if (!route.ready) return
        // A failed remeasurement must not leave an old, possibly wrong correction active.
        store.invalidate(route)
        _state.update { CalibrationUiState(route = route, runningSession = 1) }
        viewModelScope.launch {
            try {
                if (!devices.startRouting(route)) {
                    fail(STATUS_AUDIO)
                    return@launch
                }
                val sessions = mutableListOf<SessionMeasurement>()
                for (index in 1..LatencyCalibration.SESSION_COUNT) {
                    _state.update { it.copy(runningSession = index) }
                    val session = withContext(Dispatchers.Default) {
                        SessionMeasurement.fromNative(
                            NativeAudio.runCalibrationSession(
                                route.output!!.id, route.input!!.id, route.bluetooth, route.communication,
                                route.inputChannel?.code ?: InputChannel.DEVICE_CODE
                            )
                        )
                    }
                    if (!route.samePorts(devices.route())) {
                        fail(STATUS_ROUTE_CHANGED)
                        return@launch
                    }
                    Log.i(TAG, "Calibration session $index: $session")
                    sessions += session
                    // Let room reverberation and the audio HAL settle before reopening the streams.
                    delay(SESSION_PAUSE_MILLIS)
                }
                val result = LatencyCalibration.aggregate(sessions, System.currentTimeMillis())
                Log.i(TAG, "Calibration result: $result")
                when {
                    result == null -> fail(sessions.firstOrNull { !it.isValid }?.status ?: SessionMeasurement.STATUS_NO_SIGNAL)
                    result.isStable(route.maxSpreadMs) -> {
                        store.save(result, route)
                        _state.update { it.copy(saved = result, runningSession = null) }
                    }
                    else -> fail(if (result.xruns > 0) STATUS_DROPOUTS else STATUS_UNSTABLE, result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Calibration failed", error)
                fail(STATUS_AUDIO)
            } finally {
                devices.stopRouting()
            }
        }
    }

    fun reportMissingPermission() = fail(STATUS_PERMISSION_DENIED)

    private fun fail(status: Int, rejected: CalibrationResult? = null) {
        _state.update { it.copy(runningSession = null, errorStatus = status, rejected = rejected) }
    }

    companion object {
        const val STATUS_PERMISSION_DENIED = -100
        const val STATUS_ROUTE_CHANGED = -101
        const val STATUS_UNSTABLE = -102
        const val STATUS_DROPOUTS = -103
        /** Routing or stream setup failed; native open/start failures use their own negative codes. */
        const val STATUS_AUDIO = -3
        private const val SESSION_PAUSE_MILLIS = 300L
        private const val TAG = "MyAudioCalibration"
    }
}
