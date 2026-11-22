package com.martensening.my_audio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** App destinations; encoded as strings so the back stack survives process recreation. */
sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data object Calibration : Route
    data object Library : Route

    /** [autoRecord] starts the first take right away (record button on the home screen). */
    data class Project(val id: Long, val autoRecord: Boolean = false) : Route

    fun encode(): String = when (this) {
        Home -> "home"
        Settings -> "settings"
        Calibration -> "calibration"
        Library -> "library"
        is Project -> "project/$id/$autoRecord"
    }

    companion object {
        fun decode(value: String): Route? = when {
            value == "home" -> Home
            value == "settings" -> Settings
            value == "calibration" -> Calibration
            value == "library" -> Library
            value.startsWith("project/") -> value.split('/').let { parts ->
                parts.getOrNull(1)?.toLongOrNull()?.let { Project(it, parts.getOrNull(2) == "true") }
            }
            else -> null
        }
    }
}

private val backStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { values -> values.mapNotNull(Route::decode).toMutableStateList() }
)

/** [requestedRoute] (debug launches) replaces the back stack with Home plus that route. */
@Composable
fun MyAudioApp(requestedRoute: Route? = null, onRouteHandled: () -> Unit = {}) {
    val backStack = rememberSaveable(saver = backStackSaver) { mutableStateListOf<Route>(Route.Home) }
    LaunchedEffect(requestedRoute) {
        if (requestedRoute == null) return@LaunchedEffect
        backStack.clear()
        backStack.add(Route.Home)
        if (requestedRoute != Route.Home) backStack.add(requestedRoute)
        onRouteHandled()
    }
    val navigate: (Route) -> Unit = { backStack.add(it) }
    val navigateUp: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    BackHandler(enabled = backStack.size > 1) { navigateUp() }

    when (val route = backStack.last()) {
        Route.Home -> HomeScreen(
            onOpenSettings = { navigate(Route.Settings) },
            onOpenLibrary = { navigate(Route.Library) },
            onOpenProject = { id, autoRecord -> navigate(Route.Project(id, autoRecord)) }
        )
        Route.Settings -> SettingsScreen(
            onBack = navigateUp,
            onOpenCalibration = { navigate(Route.Calibration) }
        )
        Route.Calibration -> CalibrationScreen(onBack = navigateUp)
        Route.Library -> LibraryScreen(onBack = navigateUp)
        is Route.Project -> ProjectScreen(
            projectId = route.id,
            autoRecord = route.autoRecord,
            // Clear the flag so a recreated screen never starts a second take on its own.
            onAutoRecordHandled = { backStack[backStack.lastIndex] = route.copy(autoRecord = false) },
            onBack = navigateUp,
            onOpenCalibration = { navigate(Route.Calibration) }
        )
    }
}
