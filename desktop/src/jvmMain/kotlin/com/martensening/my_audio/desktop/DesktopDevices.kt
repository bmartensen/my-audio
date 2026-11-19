package com.martensening.my_audio.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Playback and recording device of the desktop app, chosen in the settings like on Android.
 * Devices are identified by name; "" is the system default. The choice is stored in `devices.json`.
 */
class DesktopDevices(root: File) {
    private val file = File(root, "devices.json")

    /** Selected names; a device that is not connected falls back to the default (see [route]). */
    var output by mutableStateOf("")
        private set
    var input by mutableStateOf("")
        private set

    init {
        runCatching {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            output = json["output"]?.jsonPrimitive?.content.orEmpty()
            input = json["input"]?.jsonPrimitive?.content.orEmpty()
        }
        DesktopNative.selectDevices(output, input)
    }

    fun outputs(): List<String> = DesktopNative.deviceNames(false).toList()

    fun inputs(): List<String> = DesktopNative.deviceNames(true).toList()

    fun select(output: String = this.output, input: String = this.input) {
        this.output = output
        this.input = input
        DesktopNative.selectDevices(output, input)
        file.writeText(buildJsonObject {
            put("output", output)
            put("input", input)
        }.toString())
    }

    /** The devices a transport uses right now: the selection if connected, otherwise the defaults. */
    fun route(): DesktopRoute {
        val usedOutput = output.takeIf { it in outputs() }.orEmpty()
        val usedInput = input.takeIf { it in inputs() }.orEmpty()
        return DesktopRoute(usedOutput, usedInput)
    }
}
