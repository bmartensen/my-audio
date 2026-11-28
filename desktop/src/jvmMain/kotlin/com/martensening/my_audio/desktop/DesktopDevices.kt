package com.martensening.my_audio.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.martensening.my_audio.audio.InputChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Playback and recording device of the desktop app, chosen in the settings like on Android.
 * Devices are identified by name; "" is the system default. The choice, and the recorded channel of
 * each two-channel input, are stored in `devices.json`.
 */
class DesktopDevices(root: File) {
    private val file = File(root, "devices.json")

    /** Selected names; a device that is not connected falls back to the default (see [route]). */
    var output by mutableStateOf("")
        private set
    var input by mutableStateOf("")
        private set

    /** Recorded channel per input name, as on Android. */
    private var inputChannels = emptyMap<String, InputChannel>()

    /** Channel counts per input name, valid while the list of connected inputs stays the same. */
    private var channelCounts = emptyMap<String, Int>()
    private var countedInputs = emptyList<String>()

    init {
        runCatching {
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            output = json["output"]?.jsonPrimitive?.content.orEmpty()
            input = json["input"]?.jsonPrimitive?.content.orEmpty()
            inputChannels = json["inputChannels"]?.jsonObject.orEmpty()
                .mapValues { (_, value) -> InputChannel.fromName(value.jsonPrimitive.content) }
        }
        DesktopNative.selectDevices(output, input)
    }

    fun outputs(): List<String> = DesktopNative.deviceNames(false).toList()

    fun inputs(): List<String> = DesktopNative.deviceNames(true).toList()

    @Synchronized
    fun select(output: String = this.output, input: String = this.input) {
        this.output = output
        this.input = input
        DesktopNative.selectDevices(output, input)
        save()
    }

    /** Remembers the recorded channel of the input [name] ("" = system default). */
    @Synchronized
    fun selectInputChannel(name: String, channel: InputChannel) {
        inputChannels = inputChannels + (name to channel)
        save()
    }

    /** The devices a transport uses right now: the selection if connected, otherwise the defaults. */
    @Synchronized
    fun route(): DesktopRoute {
        val inputs = inputs()
        val usedOutput = output.takeIf { it in outputs() }.orEmpty()
        val usedInput = input.takeIf { it in inputs }.orEmpty()
        val channel = (inputChannels[usedInput] ?: InputChannel.DEFAULT).takeIf { channelCount(usedInput, inputs) >= 2 }
        return DesktopRoute(usedOutput, usedInput, inputChannel = channel)
    }

    /** Asking the driver opens the device, so the answer is kept until an input is connected or removed. */
    private fun channelCount(name: String, inputs: List<String>): Int {
        if (inputs != countedInputs) {
            countedInputs = inputs
            channelCounts = emptyMap()
        }
        return channelCounts[name] ?: DesktopNative.inputChannels().also { channelCounts = channelCounts + (name to it) }
    }

    private fun save() = file.writeText(buildJsonObject {
        put("output", output)
        put("input", input)
        put("inputChannels", buildJsonObject {
            inputChannels.forEach { (name, channel) -> put(name, JsonPrimitive(channel.name)) }
        })
    }.toString())
}
