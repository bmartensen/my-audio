package com.martensening.my_audio.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File

/**
 * Window position and size between runs, in `window.json` next to the projects. The first start
 * opens a 16:10 window centred on the screen; later starts restore the last window if it is still
 * (mostly) on one of the connected screens, otherwise they fall back to the centred default.
 */
object DesktopWindowState {
    private val file get() = File(DesktopStorage.root, "window.json")
    private val defaultSize = DpSize(1280.dp, 800.dp)

    fun initial(): WindowState {
        restore()?.let { return it }
        return WindowState(position = WindowPosition(Alignment.Center), size = fittingDefault())
    }

    fun save(state: WindowState) = runCatching {
        val maximized = state.placement == WindowPlacement.Maximized
        // A maximised window keeps the last normal bounds, so restoring it later is not full-screen sized.
        val previous = if (maximized) runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() else null
        val position = state.position
        file.writeText(buildJsonObject {
            put("maximized", maximized)
            if (previous != null) {
                previous["x"]?.let { put("x", it.jsonPrimitive.float) }
                previous["y"]?.let { put("y", it.jsonPrimitive.float) }
                put("width", previous["width"]?.jsonPrimitive?.float ?: defaultSize.width.value)
                put("height", previous["height"]?.jsonPrimitive?.float ?: defaultSize.height.value)
            } else {
                if (position is WindowPosition.Absolute) {
                    put("x", position.x.value)
                    put("y", position.y.value)
                }
                put("width", state.size.width.value)
                put("height", state.size.height.value)
            }
        }.toString())
    }

    private fun restore(): WindowState? = runCatching {
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        val width = json.getValue("width").jsonPrimitive.float
        val height = json.getValue("height").jsonPrimitive.float
        val x = json["x"]?.jsonPrimitive?.float
        val y = json["y"]?.jsonPrimitive?.float
        val maximized = json["maximized"]?.jsonPrimitive?.content == "true"
        val visible = x != null && y != null && onScreen(Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt()))
        WindowState(
            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (visible) WindowPosition(x!!.dp, y!!.dp) else WindowPosition(Alignment.Center),
            size = DpSize(width.dp, height.dp)
        )
    }.getOrNull()

    /** At least a third of the window's title area lies on a connected screen. */
    private fun onScreen(window: Rectangle): Boolean {
        val titleBar = Rectangle(window.x, window.y, window.width, 40)
        return screens().any { screen ->
            val overlap = screen.intersection(titleBar)
            !overlap.isEmpty && overlap.width >= window.width / 3
        }
    }

    /** The default size, shrunk to 90 % of the main screen if that is smaller. */
    private fun fittingDefault(): DpSize {
        val screen = runCatching {
            val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            // Bounds are in the same scaled units Compose uses for window positions and sizes.
            device.bounds
        }.getOrNull() ?: return defaultSize
        val width = minOf(defaultSize.width.value, screen.width * 0.9f)
        val height = minOf(defaultSize.height.value, screen.height * 0.9f)
        return DpSize(width.dp, height.dp)
    }

    private fun screens(): List<Rectangle> = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    }.getOrDefault(emptyList())
}
