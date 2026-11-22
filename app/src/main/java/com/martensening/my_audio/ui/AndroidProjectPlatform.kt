package com.martensening.my_audio.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import com.martensening.my_audio.audio.AudioSettings
import android.net.Uri
import com.martensening.my_audio.audio.AudioDeviceRoute
import com.martensening.my_audio.audio.AudioDevices
import com.martensening.my_audio.audio.AudioImport
import com.martensening.my_audio.audio.AudioTransportService
import com.martensening.my_audio.audio.CalibrationStore
import com.martensening.my_audio.audio.NativeAudio
import com.martensening.my_audio.audio.OggExporter
import com.martensening.my_audio.audio.TransportRoute
import com.martensening.my_audio.audio.TunerCapture
import com.martensening.my_audio.data.EffectLibrary
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.get
import java.io.File
import kotlin.math.roundToLong

/** Android side of a [ProjectSession]: AudioManager routing, stored calibrations, foreground service, MediaCodec. */
class AndroidProjectPlatform(private val application: Application) : ProjectPlatform {
    init {
        // The buffer choice must reach the engine before any stream opens.
        AudioSettings.forRoot(application.filesDir)
    }

    private val devices = AudioDevices(application)
    private val calibrationStore = CalibrationStore(application)

    /** Live audition over the selected devices; warns for the loudspeaker (feedback) and Bluetooth (delay). */
    fun liveInput(): LiveInput = PlatformLiveInput(
        platform = this,
        hasPermission = {
            application.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        },
        warningOf = { route ->
            (route as? AudioDeviceRoute)?.output?.let { output ->
                when {
                    output.bluetooth -> LiveWarning.Bluetooth
                    output.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> LiveWarning.Speaker
                    else -> null
                }
            }
        }
    )

    override val repository = ProjectRepository.get(application)
    override val effectLibrary = EffectLibrary.forRoot(application.filesDir)
    override val cacheDir: File get() = application.cacheDir

    override fun route(): TransportRoute = devices.route()

    override fun latencyFrames(route: TransportRoute): Long? =
        (route as? AudioDeviceRoute)?.let(calibrationStore::load)?.let {
            (it.latencyMs * NativeAudio.SAMPLE_RATE / 1000.0).roundToLong()
        }

    override suspend fun startRouting(route: TransportRoute) = devices.startRouting(route as AudioDeviceRoute)

    override fun stopRouting() = devices.stopRouting()

    override suspend fun beginTransport(session: ProjectSession, recording: Boolean) =
        AudioTransportService.begin(application, session, recording)

    override fun endTransport() = AudioTransportService.end(application)

    override suspend fun encodeMix(samples: FloatArray, sampleRate: Int, channels: Int, target: File) =
        OggExporter.write(samples, sampleRate, channels, target)

    override suspend fun runTuner(onInterrupted: () -> Unit, onReady: (String) -> Unit, onPitch: (Double?, Float, Boolean) -> Unit) =
        TunerCapture.run(application, onInterrupted, onReady, onPitch)
}

/** A file picked with the Android document picker, decoded with the platform codecs. */
class UriImportSource(private val context: Context, private val uri: Uri) : ImportSource {
    override val displayName get() = AudioImport.displayName(context, uri)

    override suspend fun decodeTo(target: File, sampleRate: Int) = AudioImport.decodeToWav(context, uri, target, sampleRate)
}
