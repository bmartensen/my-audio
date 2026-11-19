package com.martensening.my_audio.audio

import com.martensening.my_audio.platform.openPcmOutput
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Plays an interleaved stereo loop endlessly on its own thread. A new loop replaces the playing
 * one at the same position with a short crossfade, so a changed effect setting is heard without
 * a restart or a click. Independent of the native transport.
 */
class LoopPlayer {
    /** One playback thread; a stopped session never touches the output again. */
    private class Session {
        val running = AtomicBoolean(true)
        val next = AtomicReference<FloatArray?>(null)
    }

    private var session: Session? = null

    val isPlaying get() = session != null

    /** Starts playing [loop], or hands it to the running playback. */
    @Synchronized
    fun play(loop: FloatArray) {
        if (loop.size < CHANNELS) return
        session?.let {
            it.next.set(loop)
            return
        }
        val started = Session().also { it.next.set(loop) }
        session = started
        thread(name = "EffectPreview", isDaemon = true) { run(started) }
    }

    @Synchronized
    fun stop() {
        session?.running?.set(false)
        session = null
    }

    private fun run(session: Session) {
        val output = openPcmOutput(NativeAudio.SAMPLE_RATE, CHANNELS) ?: run {
            session.running.set(false)
            return
        }
        try {
            var current = session.next.getAndSet(null) ?: return
            var previous: FloatArray? = null
            var fade = 0
            var frame = 0
            val chunk = FloatArray(CHUNK_FRAMES * CHANNELS)
            while (session.running.get()) {
                session.next.getAndSet(null)?.let { incoming ->
                    previous = current
                    current = incoming
                    fade = 0
                }
                val frames = current.size / CHANNELS
                for (i in 0 until CHUNK_FRAMES) {
                    frame %= frames
                    var left = current[frame * CHANNELS]
                    var right = current[frame * CHANNELS + 1]
                    previous?.let { old ->
                        val oldFrame = frame % (old.size / CHANNELS)
                        val t = fade.toFloat() / FADE_FRAMES
                        left = old[oldFrame * CHANNELS] * (1f - t) + left * t
                        right = old[oldFrame * CHANNELS + 1] * (1f - t) + right * t
                        if (++fade >= FADE_FRAMES) previous = null
                    }
                    chunk[i * CHANNELS] = left
                    chunk[i * CHANNELS + 1] = right
                    frame++
                }
                output.write(chunk, chunk.size)
            }
        } finally {
            output.close()
        }
    }

    private companion object {
        const val CHANNELS = 2
        const val CHUNK_FRAMES = 480        // 10 ms per write
        const val FADE_FRAMES = 960         // 20 ms crossfade when the loop changes
    }
}
