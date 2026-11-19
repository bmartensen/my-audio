package com.martensening.my_audio.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.edit
import com.martensening.my_audio.R
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * One selectable audio port. [key] identifies the device across reconnects (type, address and
 * product name); [id] is the current Android port ID and is never stored.
 */
data class AudioEndpoint(
    val id: Int,
    val key: String,
    val name: String,
    val type: Int,
    val address: String = "",
    /** Highest channel count the input offers; only USB inputs report more than one. */
    val channels: Int = 1
) {
    val bluetooth get() = isBluetooth(type)
}

/** Playback and recording devices of one transport; a calibration belongs to exactly this pair. */
data class AudioDeviceRoute(
    val output: AudioEndpoint?,
    val input: AudioEndpoint?,
    override val inputChannel: InputChannel? = null
) : TransportRoute {
    override val outputId get() = output?.id ?: 0
    override val inputId get() = input?.id ?: 0
    override val outputBluetooth get() = output?.bluetooth == true

    /** A Bluetooth microphone needs the hands-free (SCO) link while the take runs. */
    override val communication get() = input?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

    override val bluetooth get() = output?.bluetooth == true || input?.bluetooth == true

    /** A headset cannot record hands-free and play music at the same time. */
    override val ready get() = output != null && input != null && !(communication && output.bluetooth)

    val maxSpreadMs get() = LatencyCalibration.maxSpreadMs(bluetooth)

    /** Calibration key: measuring method plus both device identities. */
    val key: String?
        get() {
            if (!ready) return null
            val method = when {
                communication -> "bluetooth-mic-v2"
                bluetooth -> "bluetooth-warmup-v1"
                else -> "v1"
            } + if (inputChannel != null) "-2ch" else ""  // stereo input stream since 0.8.1, measured anew
            return JSONArray(listOf(method, output!!.key, input!!.key)).toString()
        }

    val builtIn get() = output?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
        input?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC

    fun samePorts(other: AudioDeviceRoute) =
        key == other.key && output?.id == other.output?.id && input?.id == other.input?.id

    override val hasOutput get() = output != null
    override val hasInput get() = input != null

    override fun samePorts(other: TransportRoute) = other is AudioDeviceRoute && samePorts(other)

    override fun sameOutput(other: TransportRoute) = other is AudioDeviceRoute && output == other.output

    override fun withoutInput() = copy(input = null, inputChannel = null)
}

/** Lists the connected devices, stores the selection and resolves it into the current ports. */
class AudioDevices(context: Context) {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val preferences = context.getSharedPreferences("audio_devices", Context.MODE_PRIVATE)
    private var communicationActive = false
    private var previousMode = AudioManager.MODE_NORMAL

    init {
        // Hands-free playback was offered briefly; such a selection returns to the default output.
        if (selectedType(input = false) == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) select(input = false, endpoint = null)
    }

    fun devices(input: Boolean): List<AudioEndpoint> {
        val flags = if (input) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
        val types = if (input) INPUT_TYPES else OUTPUT_TYPES
        return audio.getDevices(flags)
            .filter { device ->
                // Android also exposes the rear camera microphone as TYPE_BUILTIN_MIC.
                device.type in types && !(input && device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && device.address == "back")
            }
            .map { device ->
                val builtIn = device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                    device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                val identity = JSONArray(listOf(device.type, device.address,
                    if (builtIn) "" else device.productName.toString())).toString()
                // A USB interface such as the Behringer UM2 delivers its two inputs as left/right.
                val channels = if (input && device.type in USB_TYPES) device.channelCounts.maxOrNull() ?: 1 else 1
                AudioEndpoint(device.id, identity, deviceName(device), device.type, device.address, channels)
            }
            .sortedWith(compareBy({ !it.isBuiltIn() }, { it.name }, { it.id }))
    }

    private fun AudioEndpoint.isBuiltIn() =
        type == AudioDeviceInfo.TYPE_BUILTIN_MIC || type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

    private fun deviceName(device: AudioDeviceInfo): String = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> context.getString(R.string.audio_internal_mic)
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.audio_speaker)
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(
            if (device.isSource) R.string.audio_headset_mic else R.string.audio_wired_headphones
        )
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> context.getString(R.string.audio_hands_free_device, device.productName)
        else -> device.productName.toString()
    }

    fun selection(input: Boolean): String? = preferences.getString(prefix(input), null)

    fun selectedName(input: Boolean): String? = preferences.getString("${prefix(input)}_name", null)

    private fun selectedType(input: Boolean): Int? = selection(input)?.let { JSONArray(it).optInt(0) }

    /**
     * Saves a selection; null selects the default (system output or built-in microphone). A Bluetooth
     * microphone plays through the phone speaker, and Bluetooth playback records with the built-in one.
     */
    fun select(input: Boolean, endpoint: AudioEndpoint?) {
        preferences.edit {
            putSelection(input, endpoint)
            val bluetoothMicrophone = endpoint?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            if (input && bluetoothMicrophone && selectedType(input = false).let { it == null || isBluetooth(it) }) {
                putSelection(input = false, devices(input = false).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                })
            } else if (!input && (endpoint == null || endpoint.bluetooth) &&
                selectedType(input = true) == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            ) {
                putSelection(input = true, endpoint = null)
            }
        }
    }

    private fun SharedPreferences.Editor.putSelection(input: Boolean, endpoint: AudioEndpoint?) {
        putString(prefix(input), endpoint?.key)
        putString("${prefix(input)}_name", endpoint?.name)
    }

    private fun prefix(input: Boolean) = if (input) "input" else "output"

    /** Recorded channel of a two-channel input, remembered per device. */
    fun inputChannel(endpoint: AudioEndpoint): InputChannel =
        InputChannel.fromName(preferences.getString("input_channel:${endpoint.key}", null))

    fun selectInputChannel(endpoint: AudioEndpoint, channel: InputChannel) =
        preferences.edit { putString("input_channel:${endpoint.key}", channel.name) }

    /** Opens the hands-free link of a Bluetooth microphone, only during calibration or a take. */
    @Suppress("DEPRECATION")
    suspend fun startRouting(route: AudioDeviceRoute): Boolean {
        if (!route.communication) return true
        val microphone = route.input ?: return false
        previousMode = audio.mode
        communicationActive = true
        try {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            // The headset's SCO output is the communication device that carries its microphone.
            val target = if (Build.VERSION.SDK_INT >= 31) {
                audio.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && it.address == microphone.address
                }?.takeIf { audio.setCommunicationDevice(it) }
                    ?: return false.also { stopRouting() }
            } else {
                audio.startBluetoothSco()
                null
            }
            repeat(50) {
                delay(100)
                val connected = if (Build.VERSION.SDK_INT >= 31) {
                    audio.communicationDevice?.id == target?.id
                } else {
                    audio.isBluetoothScoOn
                }
                if (connected) {
                    // Give the SCO link time to settle before opening the Oboe streams.
                    delay(500)
                    return true
                }
            }
        } catch (error: Exception) {
            stopRouting()
            throw error
        }
        stopRouting()
        return false
    }

    @Suppress("DEPRECATION")
    fun stopRouting() {
        if (!communicationActive) return
        communicationActive = false
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else audio.stopBluetoothSco()
        audio.mode = previousMode
    }

    fun route(): AudioDeviceRoute {
        val outputs = devices(input = false)
        val inputs = devices(input = true)
        val output = outputs.firstOrNull { it.key == selection(input = false) } ?: systemOutput(outputs)
        val input = inputs.firstOrNull { it.key == selection(input = true) }
            ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        // Bluetooth measurements belong to a connection, including across app process restarts.
        // Android allocates a new port on reconnect; BOOT_COUNT prevents reuse after reboot.
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        fun connection(endpoint: AudioEndpoint?) =
            endpoint?.let { if (it.bluetooth) it.copy(key = "${it.key}:$boot:${it.id}") else it }
        val channel = input?.takeIf { it.channels >= 2 }?.let(::inputChannel)
        return AudioDeviceRoute(connection(output), connection(input), channel)
    }

    private fun systemOutput(outputs: List<AudioEndpoint>): AudioEndpoint? {
        if (Build.VERSION.SDK_INT >= 33) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val routed = audio.getAudioDevicesForAttributes(attributes)
            // Duplicated output cannot have a single well-defined acoustic latency.
            if (routed.size != 1) return null
            return outputs.firstOrNull { it.id == routed.single().id }
        }
        // Older Android has no public media-route query. Never guess between connected outputs.
        return outputs.singleOrNull()
    }

    fun observe(onChanged: () -> Unit): () -> Unit {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onChanged()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onChanged()
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        return { audio.unregisterAudioDeviceCallback(callback) }
    }

    private companion object {
        /** Every audio adapter, except hands-free playback: it is narrowband and replaced by music playback. */
        val OUTPUT_TYPES = buildSet {
            addAll(listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC))
            if (Build.VERSION.SDK_INT >= 31) {
                addAll(listOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_HDMI_EARC))
            }
            if (Build.VERSION.SDK_INT >= 33) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }

        val USB_TYPES = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY)

        val INPUT_TYPES = buildSet {
            addAll(listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_LINE_ANALOG))
            if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}

private fun isBluetooth(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
    (Build.VERSION.SDK_INT >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
    (Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
