package com.martensening.my_audio.platform

/** Monotonic milliseconds for UI timing (Android: uptime); shared code and screens use the same clock. */
expect fun uptimeMillis(): Long

/** Monotonic milliseconds that keep counting in deep sleep, for countdowns. */
expect fun elapsedRealtimeMillis(): Long

/** Platform log (Android logcat, desktop standard error). */
expect object Log {
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, error: Throwable? = null)
}

/** Loads the native engine library (Android: from the APK; desktop: next to the app or from the build). */
expect fun loadNativeEngine()
