package com.martensening.my_audio.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Build
import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.martensening.my_audio.MainActivity
import com.martensening.my_audio.R
import com.martensening.my_audio.ui.ProjectSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps user-started playback/capture alive while another app shows sheet music. */
class AudioTransportService : Service() {
    private var session: ProjectSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var normalStop = false

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout") // Held only for user-started foreground audio; released in onDestroy.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            session?.stopTransportAsync()
            return START_NOT_STICKY
        }
        val request = pending
        if (request == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        session = request.session
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.audio_channel), NotificationManager.IMPORTANCE_LOW))
            val open = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PROJECT_ID, request.session.projectId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 1, Intent(this, AudioTransportService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_graphic_eq)
                .setContentTitle(request.session.state.value.project?.name ?: getString(R.string.app_name))
                .setContentText(getString(if (request.recording) R.string.audio_recording else R.string.audio_playing))
                .setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(if (request.recording) NotificationCompat.CATEGORY_SERVICE else NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(R.drawable.ic_stop, getString(R.string.action_stop), stop)
                .build()
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                (if (request.recording && Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            ServiceCompat.startForeground(this, NOTIFICATION, notification, type)
            val audio = getSystemService(AudioManager::class.java)
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener({ change ->
                    if (change < 0) session?.stopTransportAsync()
                }, Handler(Looper.getMainLooper()))
                .build()
            focus = focusRequest
            if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                request.ready.complete(false)
                stopSelf()
                return START_NOT_STICKY
            }
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyAudio:transport")
                .apply { setReferenceCounted(false); acquire() }
            request.ready.complete(true)
        } catch (error: Exception) {
            Log.e("MyAudioService", "Cannot start foreground audio", error)
            request.ready.complete(false)
            stopSelf()
        }
        // Never restart a microphone recording without a fresh user action.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Explicit stop finishes/saves first. Unexpected teardown must also register the WAV.
        if (!normalStop) session?.stopNow()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        wakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
        destroyed?.complete(Unit)
        destroyed = null
        if (running === this) running = null
    }

    companion object {
        private const val CHANNEL = "audio_transport"
        private const val NOTIFICATION = 1
        private const val ACTION_STOP = "com.martensening.my_audio.STOP"
        private data class Request(val session: ProjectSession, val recording: Boolean, val ready: CompletableDeferred<Boolean>)
        private var pending: Request? = null
        private var destroyed: CompletableDeferred<Unit>? = null
        private var running: AudioTransportService? = null

        suspend fun begin(context: Context, session: ProjectSession, recording: Boolean): Boolean {
            // A quick Stop → Record must not reuse the service while its old teardown is pending.
            destroyed?.let { if (withTimeoutOrNull(5000) { it.await(); true } != true) return false }
            val request = Request(session, recording, CompletableDeferred())
            pending = request
            return try {
                ContextCompat.startForegroundService(context, Intent(context, AudioTransportService::class.java))
                withTimeoutOrNull(5000) { request.ready.await() } == true
            } finally {
                if (pending === request) pending = null
            }
        }

        fun end(context: Context) {
            if (destroyed != null) return
            val completion = CompletableDeferred<Unit>()
            destroyed = completion
            running?.normalStop = true
            if (!context.stopService(Intent(context, AudioTransportService::class.java))) {
                completion.complete(Unit)
                destroyed = null
            }
        }
    }
}
