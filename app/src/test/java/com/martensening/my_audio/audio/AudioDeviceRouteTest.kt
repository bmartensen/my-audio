package com.martensening.my_audio.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceRouteTest {
    private val speaker = AudioEndpoint(3, "speaker", "Speaker", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    private val microphone = AudioEndpoint(21, "mic", "Microphone", AudioDeviceInfo.TYPE_BUILTIN_MIC)
    private val headphones = AudioEndpoint(70, "headphones", "Headphones", AudioDeviceInfo.TYPE_USB_HEADSET)
    private val headset = AudioEndpoint(80, "headset", "Headset", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "00:11")
    private val headsetMic = AudioEndpoint(81, "headset-mic", "Headset", AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "00:11")

    @Test
    fun calibrationBelongsToBothDevicesAndSurvivesNewPortIds() {
        val route = AudioDeviceRoute(speaker, microphone)
        assertNotEquals(route.key, route.copy(output = headphones).key)
        assertNotEquals(route.key, route.copy(input = headphones).key)
        assertEquals(route.key, route.copy(output = speaker.copy(id = 90)).key)
        assertFalse(route.samePorts(route.copy(output = speaker.copy(id = 90))))
    }

    @Test
    fun missingDeviceCannotHaveCalibrationAndLegacyOnlyFitsBuiltInPair() {
        assertNull(AudioDeviceRoute(null, microphone).key)
        assertNull(AudioDeviceRoute(speaker, null).key)
        assertFalse(AudioDeviceRoute(headphones, microphone).builtIn)
        assertTrue(AudioDeviceRoute(speaker, microphone).builtIn)
    }

    @Test
    fun deviceNamesDoNotChangeCalibrationIdentity() {
        val route = AudioDeviceRoute(speaker, microphone)
        assertEquals(route.key, route.copy(input = microphone.copy(name = "Internes Mikrofon")).key)
    }

    @Test
    fun bluetoothMicrophoneRecordsHandsFreeWhileThePhonePlays() {
        val route = AudioDeviceRoute(speaker, headsetMic)
        assertTrue(route.ready)
        assertTrue(route.communication)
        assertTrue(route.bluetooth)
        assertNotEquals(route.key, AudioDeviceRoute(headset, microphone).key)
    }

    @Test
    fun headsetCannotRecordHandsFreeAndPlayMusicAtOnce() {
        val route = AudioDeviceRoute(headset, headsetMic)
        assertFalse(route.ready)
        assertNull(route.key)
    }

    @Test
    fun musicPlaybackOverBluetoothUsesTheBuiltInMicrophoneWithBluetoothTolerance() {
        val route = AudioDeviceRoute(headset, microphone)
        assertFalse(route.communication)
        assertEquals(LatencyCalibration.MAX_BLUETOOTH_SPREAD_MS, route.maxSpreadMs, 0.0)
        assertEquals(LatencyCalibration.MAX_SPREAD_MS, AudioDeviceRoute(headphones, microphone).maxSpreadMs, 0.0)
    }
}
