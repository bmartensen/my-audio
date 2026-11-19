package com.martensening.my_audio.platform

import java.time.LocalTime

actual fun uptimeMillis(): Long = System.nanoTime() / 1_000_000

actual fun elapsedRealtimeMillis(): Long = System.nanoTime() / 1_000_000

actual object Log {
    private fun print(level: Char, tag: String, message: String) = System.err.println("${LocalTime.now()} $level/$tag: $message")
    actual fun i(tag: String, message: String) = print('I', tag, message)
    actual fun w(tag: String, message: String) = print('W', tag, message)
    actual fun e(tag: String, message: String, error: Throwable?) {
        print('E', tag, message)
        error?.printStackTrace()
    }
}

/**
 * The packaged app keeps the library in its resources directory; `gradlew :desktop:run` points
 * `myaudio.native.dir` at the CMake output. Loading twice is harmless.
 */
actual fun loadNativeEngine() {
    val name = System.mapLibraryName("myaudio")
    val library = listOf("compose.application.resources.dir", "myaudio.native.dir")
        .mapNotNull { System.getProperty(it)?.let { dir -> java.io.File(dir, name) } }
        .firstOrNull { it.isFile }
    if (library != null) System.load(library.absolutePath) else System.loadLibrary("myaudio")
}
