package com.martensening.my_audio.platform

import android.os.SystemClock

actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

actual fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

actual object Log {
    actual fun i(tag: String, message: String) { android.util.Log.i(tag, message) }
    actual fun w(tag: String, message: String) { android.util.Log.w(tag, message) }
    actual fun e(tag: String, message: String, error: Throwable?) { android.util.Log.e(tag, message, error) }
}

actual fun loadNativeEngine() = System.loadLibrary("myaudio")
