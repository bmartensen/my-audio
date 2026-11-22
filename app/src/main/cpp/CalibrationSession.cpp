#include "CalibrationSession.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>
#include <android/log.h>

#include "Correlation.h"
#include "LiveChain.h"
#include "StreamWarmup.h"

namespace myaudio {
namespace {

constexpr double kPreRollSeconds = 0.3;
constexpr double kSweepSeconds = 0.05;
constexpr int kSweepCount = 6;
constexpr int kMinValidSweeps = 4;
constexpr double kMinSnrDb = 15.0;

/**
 * Writes the test signal to the output and appends every input frame to a contiguous
 * recording. Both indices start at the first full-duplex callback, so a correlation lag
 * between them is exactly the offset an overdub recorded the same way has to remove.
 */
class DuplexRecorder : public oboe::FullDuplexStream {
public:
    /** Must be called before the streams start; the sample rate is only known after opening. */
    void prepare(std::vector<float> signal, int recordingLength, int warmupFrames, InputChannel inputChannel) {
        mWarmup.reset(warmupFrames);
        mInputChannel = inputChannel;
        mSignal = std::move(signal);
        mRecording.assign(recordingLength, 0.0f);
    }

    oboe::DataCallbackResult onBothStreamsReady(const void *inputData, int numInputFrames,
                                                void *outputData, int numOutputFrames) override {
        if (mWarmup.discard(numOutputFrames)) {
            std::fill_n(static_cast<float *>(outputData),
                        numOutputFrames * getOutputStream()->getChannelCount(), 0.0f);
            return oboe::DataCallbackResult::Continue;
        }
        if (!mStarted) {
            mStarted = true;
            mOutputBase = getOutputStream()->getFramesWritten();
            mInputBase = getInputStream()->getFramesRead() - numInputFrames;
        }

        auto *output = static_cast<float *>(outputData);
        const int outputChannels = getOutputStream()->getChannelCount();
        for (int frame = 0; frame < numOutputFrames; ++frame, ++mOutputPosition) {
            const float sample = mOutputPosition < static_cast<int64_t>(mSignal.size())
                                 ? mSignal[mOutputPosition] : 0.0f;
            for (int channel = 0; channel < outputChannels; ++channel) {
                output[frame * outputChannels + channel] = sample;
            }
        }

        if (!mDone.load(std::memory_order_relaxed)) {
            const auto *input = static_cast<const float *>(inputData);
            const int inputChannels = getInputStream()->getChannelCount();
            const auto capacity = static_cast<int64_t>(mRecording.size());
            for (int frame = 0; frame < numInputFrames && mInputPosition < capacity; ++frame) {
                mRecording[mInputPosition++] = monoInputSample(input + frame * inputChannels, inputChannels, mInputChannel);
            }
            if (mInputPosition >= capacity) mDone.store(true, std::memory_order_release);
        }
        return oboe::DataCallbackResult::Continue;
    }

    bool isDone() const { return mDone.load(std::memory_order_acquire); }
    bool isWarmedUp() const { return mWarmup.ready(); }
    const std::vector<float> &recording() const { return mRecording; }
    int64_t outputBase() const { return mOutputBase; }
    int64_t inputBase() const { return mInputBase; }

private:
    std::vector<float> mSignal;
    std::vector<float> mRecording;
    bool mStarted = false;
    InputChannel mInputChannel = InputChannel::Device;
    StreamWarmup mWarmup;
    int64_t mOutputPosition = 0;
    int64_t mInputPosition = 0;
    int64_t mOutputBase = 0;
    int64_t mInputBase = 0;
    std::atomic<bool> mDone{false};
};

double median(std::vector<double> values) {
    std::sort(values.begin(), values.end());
    const size_t middle = values.size() / 2;
    return values.size() % 2 ? values[middle] : 0.5 * (values[middle - 1] + values[middle]);
}

}  // namespace

SessionResult runCalibrationSession(int outputDeviceId, int inputDeviceId, bool bluetooth, bool handsFreeInput,
                                    InputChannel inputChannel) {
    const double sweepSpacingSeconds = bluetooth ? 1.0 : 0.4;
    const double maxLatencySeconds = bluetooth ? 0.8 : 0.3; // below the sweep spacing
    SessionResult result;
    // Declared before the streams so it outlives every callback.
    DuplexRecorder recorder;

    oboe::AudioStreamBuilder outputBuilder;
    outputBuilder.setDirection(oboe::Direction::Output)
            ->setDeviceId(outputDeviceId)
            ->setDataCallback(&recorder)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setChannelConversionAllowed(true)
            ->setSampleRate(48000)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music);
    std::shared_ptr<oboe::AudioStream> output;
    if (outputBuilder.openStream(output) != oboe::Result::OK) {
        result.status = kOpenOutputFailed;
        return result;
    }
    if (output->getDeviceId() != outputDeviceId) {
        output->close();
        result.status = kOpenOutputFailed;
        return result;
    }
    // The same global buffer choice as takes and live monitoring (MultitrackEngine::openOutput).
    if (bufferSetting() > 0) output->setBufferSizeInFrames(output->getFramesPerBurst() * bufferSetting());
    const int sampleRate = output->getSampleRate();
    result.sampleRate = sampleRate;

    oboe::AudioStreamBuilder inputBuilder;
    inputBuilder.setDirection(oboe::Direction::Input)
            ->setDeviceId(inputDeviceId)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            // Same channel layout as the take, so the measured latency applies unchanged.
            ->setChannelCount(inputChannel == InputChannel::Device ? oboe::ChannelCount::Mono : oboe::ChannelCount::Stereo)
            ->setChannelConversionAllowed(true)
            ->setSampleRate(sampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setInputPreset(handsFreeInput ? oboe::InputPreset::VoiceRecognition : oboe::InputPreset::VoicePerformance)
            ->setBufferCapacityInFrames(output->getBufferCapacityInFrames() * 2);
    std::shared_ptr<oboe::AudioStream> input;
    if (inputBuilder.openStream(input) != oboe::Result::OK) {
        output->close();
        result.status = kOpenInputFailed;
        return result;
    }
    if (input->getDeviceId() != inputDeviceId) {
        input->close();
        output->close();
        result.status = kOpenInputFailed;
        return result;
    }

    // A hands-free microphone can be narrowband (roughly 300–3400 Hz); a wideband sweep is unsuitable.
    const std::vector<float> sweep = makeSweep(sampleRate, kSweepSeconds,
                                              handsFreeInput ? 300.0 : 500.0,
                                              handsFreeInput ? 3000.0 : 10000.0, 0.6f);
    const int preRoll = static_cast<int>(kPreRollSeconds * sampleRate);
    const int spacing = static_cast<int>(sweepSpacingSeconds * sampleRate);
    const int maxLag = static_cast<int>(maxLatencySeconds * sampleRate);
    std::vector<float> signal(preRoll + kSweepCount * spacing, 0.0f);
    for (int index = 0; index < kSweepCount; ++index) {
        std::copy(sweep.begin(), sweep.end(), signal.begin() + preRoll + index * spacing);
    }
    const int recordingLength = static_cast<int>(signal.size()) + maxLag + static_cast<int>(sweep.size());
    const double signalSeconds = static_cast<double>(signal.size()) / sampleRate;

    recorder.prepare(std::move(signal), recordingLength,
                     bluetooth ? sampleRate * StreamWarmup::kBluetoothSeconds : 0, inputChannel);
    recorder.setSharedOutputStream(output);
    recorder.setSharedInputStream(input);

    if (recorder.start() != oboe::Result::OK) {
        output->close();
        input->close();
        result.status = kStartFailed;
        return result;
    }
    __android_log_print(ANDROID_LOG_INFO, "MyAudioCalibration", "Active devices: output %d (requested %d), input %d (requested %d)",
                        output->getDeviceId(), outputDeviceId, input->getDeviceId(), inputDeviceId);

    const auto deadline = std::chrono::steady_clock::now()
            + std::chrono::milliseconds(static_cast<int>((signalSeconds + maxLatencySeconds +
                                                          StreamWarmup::kBluetoothSeconds + 3.0) * 1000));
    int warmupXRuns = 0;
    bool baselineCaptured = false;
    while (!recorder.isDone() && std::chrono::steady_clock::now() < deadline) {
        if (!baselineCaptured && recorder.isWarmedUp()) {
            auto out = output->getXRunCount();
            auto in = input->getXRunCount();
            warmupXRuns = (out ? out.value() : 0) + (in ? in.value() : 0);
            baselineCaptured = true;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }

    // Timestamps and diagnostics are only meaningful while both streams are still running.
    auto outputTimestamp = output->getTimestamp(CLOCK_MONOTONIC);
    auto inputTimestamp = input->getTimestamp(CLOCK_MONOTONIC);
    auto outputXRuns = output->getXRunCount();
    auto inputXRuns = input->getXRunCount();
    result.xruns = std::max(0, (outputXRuns ? outputXRuns.value() : 0) +
                              (inputXRuns ? inputXRuns.value() : 0) - warmupXRuns);
    __android_log_print(ANDROID_LOG_INFO, "MyAudioCalibration", "Discarded warmup: %d xruns; measurement: %d xruns",
                        warmupXRuns, result.xruns);
    result.outputMMap = oboe::OboeExtensions::isMMapUsed(output.get());
    result.inputMMap = oboe::OboeExtensions::isMMapUsed(input.get());
    result.framesPerBurst = output->getFramesPerBurst();
    result.outputBufferFrames = output->getBufferSizeInFrames();
    const bool sameDevices = output->getDeviceId() == outputDeviceId && input->getDeviceId() == inputDeviceId;

    output->stop();
    input->stop();
    output->close();
    input->close();

    if (!sameDevices) {
        result.status = kStartFailed;
        return result;
    }
    if (!recorder.isDone()) {
        result.status = kTimeout;
        return result;
    }

    if (outputTimestamp && inputTimestamp) {
        // Time at which output frame 0 of the session left the speaker, mapped onto the input clock.
        const double outputTimeNanos = outputTimestamp.value().timestamp
                + (recorder.outputBase() - outputTimestamp.value().position) * 1e9 / sampleRate;
        const double inputPosition = inputTimestamp.value().position
                + (outputTimeNanos - inputTimestamp.value().timestamp) * sampleRate / 1e9;
        result.timestampLatencyFrames = inputPosition - recorder.inputBase();
    }

    std::vector<double> lags;
    double minSnr = INFINITY;
    for (int index = 0; index < kSweepCount; ++index) {
        const int start = preRoll + index * spacing;
        const CorrelationPeak peak = findPeak(recorder.recording(), start, sweep, maxLag);
        __android_log_print(ANDROID_LOG_INFO, "MyAudioCalibration", "Sweep %d: %.2f ms, SNR %.1f dB",
                            index + 1, peak.lagFrames * 1000.0 / sampleRate, peak.snrDb);
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
    result.chirpSpreadFrames = *std::max_element(lags.begin(), lags.end())
            - *std::min_element(lags.begin(), lags.end());
    result.minSnrDb = minSnr;
    return result;
}

}  // namespace myaudio
