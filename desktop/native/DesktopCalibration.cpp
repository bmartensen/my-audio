// Desktop counterpart of CalibrationSession.cpp: the same sweeps and analysis, but in one
// miniaudio duplex device configured exactly like DesktopEngine's recording device.
#include "CalibrationSession.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>

#include "Correlation.h"
#include "DesktopDevices.h"
#include "Log.h"
#include "ProjectMix.h"
#include "Wav.h"
#include "miniaudio.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioCalibration";
constexpr double kPreRollSeconds = 0.3;
constexpr double kSweepSeconds = 0.05;
constexpr int kSweepCount = 6;
constexpr int kMinValidSweeps = 4;
constexpr double kMinSnrDb = 15.0;
constexpr double kSweepSpacingSeconds = 0.4;
constexpr double kMaxLatencySeconds = 0.3;  // below the sweep spacing
constexpr int kChannels = 2;

/** Output frame n and input frame n start with the same duplex callback, as in a take. */
struct DuplexRecorder {
    std::vector<float> signal;
    std::vector<float> recording;
    int64_t outputPosition = 0;
    int64_t inputPosition = 0;
    std::atomic<bool> done{false};
    std::atomic<bool> lost{false};
};

void dataCallback(ma_device *device, void *outputData, const void *inputData, ma_uint32 frames) {
    auto &recorder = *static_cast<DuplexRecorder *>(device->pUserData);
    auto *output = static_cast<float *>(outputData);
    for (ma_uint32 frame = 0; frame < frames; ++frame, ++recorder.outputPosition) {
        const float sample = recorder.outputPosition < static_cast<int64_t>(recorder.signal.size())
                             ? recorder.signal[recorder.outputPosition] : 0.0f;
        for (int channel = 0; channel < kChannels; ++channel) output[frame * kChannels + channel] = sample;
    }
    if (recorder.done.load(std::memory_order_relaxed)) return;
    const auto *input = static_cast<const float *>(inputData);
    const auto capacity = static_cast<int64_t>(recorder.recording.size());
    for (ma_uint32 frame = 0; frame < frames && recorder.inputPosition < capacity; ++frame) {
        recorder.recording[recorder.inputPosition++] = input[frame];
    }
    if (recorder.inputPosition >= capacity) recorder.done.store(true, std::memory_order_release);
}

void notificationCallback(const ma_device_notification *notification) {
    if (notification->type == ma_device_notification_type_rerouted ||
        notification->type == ma_device_notification_type_stopped) {
        static_cast<DuplexRecorder *>(notification->pDevice->pUserData)->lost.store(true);
    }
}

double median(std::vector<double> values) {
    std::sort(values.begin(), values.end());
    const size_t middle = values.size() / 2;
    return values.size() % 2 ? values[middle] : 0.5 * (values[middle - 1] + values[middle]);
}

}  // namespace

SessionResult runCalibrationSession(int /* outputDeviceId */, int /* inputDeviceId */, bool /* bluetooth */,
                                    bool /* handsFreeInput */, InputChannel /* inputChannel */) {
    const int sampleRate = kProjectSampleRate;
    SessionResult result;
    result.sampleRate = sampleRate;
    DuplexRecorder recorder;

    const std::vector<float> sweep = makeSweep(sampleRate, kSweepSeconds, 500.0, 10000.0, 0.6f);
    const int preRoll = static_cast<int>(kPreRollSeconds * sampleRate);
    const int spacing = static_cast<int>(kSweepSpacingSeconds * sampleRate);
    const int maxLag = static_cast<int>(kMaxLatencySeconds * sampleRate);
    recorder.signal.assign(preRoll + kSweepCount * spacing, 0.0f);
    for (int index = 0; index < kSweepCount; ++index) {
        std::copy(sweep.begin(), sweep.end(), recorder.signal.begin() + preRoll + index * spacing);
    }
    recorder.recording.assign(recorder.signal.size() + maxLag + sweep.size(), 0.0f);
    const double signalSeconds = static_cast<double>(recorder.signal.size()) / sampleRate;

    ma_device device;
    ma_device_config config = ma_device_config_init(ma_device_type_duplex);
    config.sampleRate = sampleRate;
    config.playback.format = ma_format_f32;
    config.playback.channels = kChannels;
    config.capture.format = ma_format_f32;
    config.capture.channels = 1;
    config.performanceProfile = ma_performance_profile_low_latency;
    config.dataCallback = dataCallback;
    config.notificationCallback = notificationCallback;
    config.pUserData = &recorder;
    config.wasapi.noAutoStreamRouting = MA_TRUE;
    if (!initDevice(config, &device, kTag)) {
        result.status = kOpenOutputFailed;
        return result;
    }
    result.framesPerBurst = static_cast<int>(device.playback.internalPeriodSizeInFrames);
    result.outputBufferFrames = static_cast<int>(device.playback.internalPeriodSizeInFrames * device.playback.internalPeriods);
    if (ma_device_start(&device) != MA_SUCCESS) {
        ma_device_uninit(&device);
        result.status = kStartFailed;
        return result;
    }
    MYAUDIO_LOGI(kTag, "Duplex device: playback %s, capture %s, period %d frames", device.playback.name,
                 device.capture.name, result.framesPerBurst);

    const auto deadline = std::chrono::steady_clock::now() +
                          std::chrono::milliseconds(static_cast<int>((signalSeconds + kMaxLatencySeconds + 3.0) * 1000));
    while (!recorder.done.load(std::memory_order_acquire) && !recorder.lost.load() &&
           std::chrono::steady_clock::now() < deadline) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    // Read before closing: closing the device itself reports "stopped".
    const bool lost = recorder.lost.load();
    ma_device_uninit(&device);

    if (lost) {
        result.status = kStartFailed;
        return result;
    }
    if (!recorder.done.load()) {
        result.status = kTimeout;
        return result;
    }

    // Diagnostics: level of what the microphone heard, and the recording itself for analysis.
    float peak = 0.0f;
    double energy = 0.0;
    for (const float sample : recorder.recording) {
        peak = std::max(peak, std::fabs(sample));
        energy += static_cast<double>(sample) * sample;
    }
    MYAUDIO_LOGI(kTag, "Recording: peak %.1f dBFS, RMS %.1f dBFS", 20.0 * std::log10(std::max(peak, 1e-9f)),
                 10.0 * std::log10(std::max(energy / static_cast<double>(recorder.recording.size()), 1e-18)));
    if (const char *temp = std::getenv("TEMP")) {
        std::vector<int16_t> pcm(recorder.recording.size());
        for (size_t i = 0; i < pcm.size(); ++i) pcm[i] = toPcm16(recorder.recording[i]);
        WavWriter wav;
        if (wav.open(std::string(temp) + "/my-audio-calibration.wav", sampleRate)) {
            wav.write(pcm.data(), pcm.size());
            wav.finish();
        }
    }

    std::vector<double> lags;
    double minSnr = INFINITY;
    for (int index = 0; index < kSweepCount; ++index) {
        const int start = preRoll + index * spacing;
        const CorrelationPeak peak = findPeak(recorder.recording, start, sweep, maxLag);
        MYAUDIO_LOGI(kTag, "Sweep %d: %.2f ms, SNR %.1f dB", index + 1, peak.lagFrames * 1000.0 / sampleRate, peak.snrDb);
        if (peak.snrDb >= kMinSnrDb) {
            lags.push_back(peak.lagFrames);
            minSnr = std::min(minSnr, peak.snrDb);
        }
    }
    result.validChirps = static_cast<int>(lags.size());
    if (result.validChirps < kMinValidSweeps) {
        result.status = kNoSignal;
        return result;
    }
    result.latencyFrames = median(lags);
    result.chirpSpreadFrames = *std::max_element(lags.begin(), lags.end()) - *std::min_element(lags.begin(), lags.end());
    result.minSnrDb = minSnr;
    return result;
}

}  // namespace myaudio
