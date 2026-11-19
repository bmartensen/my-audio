package com.martensening.my_audio.desktop

import com.martensening.my_audio.audio.TransportRoute
import com.martensening.my_audio.data.EffectLibrary
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.ui.ProjectPlatform
import com.martensening.my_audio.ui.ProjectSession
import java.io.File

/**
 * Playback and recording device of a desktop transport by name ("" = system default). The ids
 * the engine sees are always 0; the native side opens the devices selected in [DesktopDevices].
 */
data class DesktopRoute(val output: String, val input: String, override val hasInput: Boolean = true) : TransportRoute {
    override val outputId = 0
    override val inputId = 0
    override val outputBluetooth = false
    override val bluetooth = false
    override val communication = false
    override val ready = true
    override val hasOutput = true

    /** Calibration key of the pair. */
    val key get() = "$output\u0000$input"

    override fun samePorts(other: TransportRoute) = other is DesktopRoute && other.output == output && other.input == input
    override fun sameOutput(other: TransportRoute) = other is DesktopRoute && other.output == output
    override fun withoutInput() = copy(hasInput = false)
}

/** Desktop side of a [ProjectSession]: no background service or routing, calibrations per device pair. */
class DesktopProjectPlatform(
    override val repository: ProjectRepository,
    private val devices: DesktopDevices,
    private val calibration: DesktopCalibrationStore,
    root: File
) : ProjectPlatform {
    override val cacheDir = File(root, "cache").apply { mkdirs() }
    override val effectLibrary = EffectLibrary.forRoot(root)

    override fun route(): TransportRoute = devices.route()

    override fun latencyFrames(route: TransportRoute) = (route as? DesktopRoute)?.let(calibration::latencyFrames)

    override suspend fun startRouting(route: TransportRoute) = true

    override fun stopRouting() = Unit

    // A desktop app keeps running in the background anyway.
    override suspend fun beginTransport(session: ProjectSession, recording: Boolean) = true

    override fun endTransport() = Unit

    override suspend fun encodeMix(samples: FloatArray, sampleRate: Int, channels: Int, target: File) =
        DesktopOggExporter.write(samples, sampleRate, channels, target)

    override suspend fun runTuner(onInterrupted: () -> Unit, onReady: (String) -> Unit, onPitch: (Double?, Float, Boolean) -> Unit) =
        DesktopTuner.run(onReady, onPitch)
}
