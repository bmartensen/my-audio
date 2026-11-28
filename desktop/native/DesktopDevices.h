#pragma once

#include <string>
#include <vector>

#include "miniaudio.h"

namespace myaudio {

/**
 * Device choice of the desktop app. Devices are identified by name (stable across restarts and
 * reconnects, unlike miniaudio's ids); an empty name means the system default. A selected device
 * that is not connected falls back to the default, as on Android.
 */
std::vector<std::string> deviceNames(bool capture);

void selectDevices(const std::string &playback, const std::string &capture);

/**
 * Channel count of the microphone a transport opens now (the selection if connected, else the default):
 * the most any of its native formats offers, 1 if unknown. Two or more offer the input channel choice.
 */
int captureChannels();

/** The shared miniaudio context (lazily initialised), used by every device the app opens. */
ma_context *audioContext();

/**
 * Points [config] at the selected devices; [playbackId]/[captureId] must outlive the device init.
 * Returns the names actually used ("" = default) in [usedPlayback]/[usedCapture].
 */
void applySelectedDevices(ma_device_config &config, ma_device_id &playbackId, ma_device_id &captureId,
                          std::string &usedPlayback, std::string &usedCapture);

/**
 * Opens [device] with the selected devices. For a duplex or capture device the microphone is tried in
 * exclusive mode first: it bypasses the driver's audio effects (echo cancellation and noise
 * suppression of notebook microphone arrays remove exactly the speaker sound an overdub and the
 * calibration need). Engine and calibration both open their devices here, so the measured
 * latency belongs to the same configuration. Returns false if no mode works.
 */
bool initDevice(ma_device_config &config, ma_device *device, const char *tag);

}  // namespace myaudio
