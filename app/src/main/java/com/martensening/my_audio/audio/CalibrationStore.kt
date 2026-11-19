package com.martensening.my_audio.audio

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/** Persists a separate calibration for each resolved input/output route in private preferences. */
class CalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    fun load(route: AudioDeviceRoute): CalibrationResult? {
        val key = route.key ?: return null
        // The previous app only calibrated speaker + built-in mic. Preserve that measurement
        // exclusively for that pair; it must never calibrate a newly connected headset.
        val json = preferences.getString(key, null)
            ?: (if (route.builtIn) preferences.getString(KEY_LATEST, null) else null)
            ?: return null
        return runCatching {
            with(JSONObject(json)) {
                CalibrationResult(
                    latencyFrames = getDouble("latencyFrames"),
                    sampleRate = getInt("sampleRate"),
                    sessionSpreadMs = getDouble("sessionSpreadMs"),
                    chirpSpreadMs = getDouble("chirpSpreadMs"),
                    timestampLatencyMs = optDouble("timestampLatencyMs").takeUnless { it.isNaN() },
                    timestampDeviationSpreadMs = optDouble("timestampDeviationSpreadMs").takeUnless { it.isNaN() },
                    minSnrDb = getDouble("minSnrDb"),
                    sessions = getInt("sessions"),
                    xruns = getInt("xruns"),
                    mmap = getBoolean("mmap"),
                    measuredAtMillis = getLong("measuredAtMillis")
                )
            }
        }.getOrNull()?.takeIf { it.isStable(route.maxSpreadMs) }
    }

    fun invalidate(route: AudioDeviceRoute) {
        preferences.edit {
            route.key?.let { remove(it) }
            if (route.builtIn) remove(KEY_LATEST)
        }
    }

    fun save(result: CalibrationResult, route: AudioDeviceRoute) {
        val key = requireNotNull(route.key)
        require(result.isStable(route.maxSpreadMs))
        val json = JSONObject()
            .put("latencyFrames", result.latencyFrames)
            .put("sampleRate", result.sampleRate)
            .put("sessionSpreadMs", result.sessionSpreadMs)
            .put("chirpSpreadMs", result.chirpSpreadMs)
            .put("timestampLatencyMs", result.timestampLatencyMs)
            .put("timestampDeviationSpreadMs", result.timestampDeviationSpreadMs)
            .put("minSnrDb", result.minSnrDb)
            .put("sessions", result.sessions)
            .put("xruns", result.xruns)
            .put("mmap", result.mmap)
            .put("measuredAtMillis", result.measuredAtMillis)
        preferences.edit { putString(key, json.toString()) }
    }

    private companion object {
        const val KEY_LATEST = "latest"
    }
}
