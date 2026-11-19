package com.martensening.my_audio

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.runtime.mutableStateOf
import com.martensening.my_audio.ui.MyAudioApp
import com.martensening.my_audio.ui.Route
import com.martensening.my_audio.ui.theme.MyAudioTheme

class MainActivity : ComponentActivity() {
    private val isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Route requested by `tools/debug_launch.ps1 -Screen <route>` in debug builds. */
    private val debugRoute = mutableStateOf<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hardware volume keys always control media volume; the app has no master volume of its own.
        volumeControlStream = AudioManager.STREAM_MUSIC
        handleDebugLaunch(intent)
        handleProjectIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val preferences = getPreferences(MODE_PRIVATE)
            if (!preferences.getBoolean("notificationsAsked", false)) {
                preferences.edit { putBoolean("notificationsAsked", true) }
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
            }
        }
        setContent {
            MyAudioTheme {
                MyAudioApp(requestedRoute = debugRoute.value, onRouteHandled = { debugRoute.value = null })
            }
        }
    }

    // After an app update the system may bring the old task to the front first; the launch
    // intent then arrives here instead of in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugLaunch(intent)
        handleProjectIntent(intent)
    }

    /** Opens the requested screen and confirms the start once the next frame is laid out. */
    private fun handleDebugLaunch(intent: Intent) {
        if (!isDebuggable) return
        intent.getStringExtra(EXTRA_DEBUG_SCREEN)?.let(Route::decode)?.let { debugRoute.value = it }
        val requestId = intent.getStringExtra(EXTRA_DEBUG_REQUEST_ID) ?: return
        window.decorView.post { Log.i(DEBUG_TAG, "Ready[$requestId]") }
    }

    private fun handleProjectIntent(intent: Intent) {
        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1)
        if (projectId > 0) debugRoute.value = Route.Project(projectId)
        intent.removeExtra(EXTRA_PROJECT_ID)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "audioProjectId"
        const val DEBUG_TAG = "MyAudioDebug"
        const val EXTRA_DEBUG_REQUEST_ID = "debugRequestId"
        const val EXTRA_DEBUG_SCREEN = "debugScreen"
    }
}
