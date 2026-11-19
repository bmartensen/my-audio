#include "DesktopEngine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <memory>

#include "DesktopDevices.h"
#include "Log.h"
#include "Metronome.h"
#include "miniaudio.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioEngine";
constexpr int kFifoSeconds = 4;
constexpr int kWriterChunkFrames = 4096;
constexpr int kChannels = 2;
// Larger than any device period, so the reference of a block always fits.
constexpr int kScratchFrames = kProjectSampleRate;

void dataCallback(ma_device *device, void *output, const void *input, ma_uint32 frames) {
    static_cast<DesktopEngine *>(device->pUserData)
            ->process(static_cast<float *>(output), static_cast<const float *>(input), frames);
}

void notificationCallback(const ma_device_notification *notification) {
    // Another device behind the stream would invalidate the calibrated latency.
    if (notification->type == ma_device_notification_type_stopped ||
        notification->type == ma_device_notification_type_rerouted) {
        static_cast<DesktopEngine *>(notification->pDevice->pUserData)->onDeviceLost();
    }
}

}  // namespace

DesktopEngine &DesktopEngine::instance() {
    static DesktopEngine engine;
    return engine;
}

bool DesktopEngine::setTracks(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mDevice != nullptr) return false;
    std::vector<float> left;
    std::vector<float> right;
    renderProjectMix(sources, master, left, right);
    mMixLeft = std::move(left);
    mMixRight = std::move(right);
    return true;
}

bool DesktopEngine::exportStems(const std::vector<TrackSource> &sources, int64_t projectFrames,
                                const std::vector<std::string> &targets) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mDevice != nullptr) return false;
    return writeStems(sources, projectFrames, targets);
}

void DesktopEngine::setMetronome(bool enabled, float bpm, float volume) {
    mMetronomeBpm.store(std::clamp(bpm, 20.0f, 400.0f), std::memory_order_relaxed);
    mMetronomeGain.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_relaxed);
    mMetronomeEnabled.store(enabled, std::memory_order_relaxed);
}

void DesktopEngine::seekPlayback(int64_t frame) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mDevice == nullptr || mRecording) return;
    mSeekRequest.store(std::clamp<int64_t>(frame, 0, mixFrames()), std::memory_order_relaxed);
}

std::vector<float> DesktopEngine::renderPlayback() {
    std::lock_guard<std::mutex> guard(mLock);
    if (mDevice != nullptr) return {};
    const auto frames = static_cast<size_t>(mixFrames());
    std::vector<float> output(frames * kChannels);
    const int64_t savedPosition = mPosition.load();
    mPosition = 0;
    constexpr int kChunk = 4096;
    for (size_t start = 0; start < frames; start += kChunk) {
        const int chunk = static_cast<int>(std::min<size_t>(kChunk, frames - start));
        mix(output.data() + start * kChannels, kChannels, chunk, nullptr, false);
    }
    mPosition = savedPosition;
    return output;
}

bool DesktopEngine::openDevice(bool duplex) {
    auto device = std::make_unique<ma_device>();
    // Same configuration as the calibration (DesktopCalibration.cpp), so its latency applies.
    ma_device_config config = ma_device_config_init(duplex ? ma_device_type_duplex : ma_device_type_playback);
    config.sampleRate = kSampleRate;
    config.playback.format = ma_format_f32;
    config.playback.channels = kChannels;
    config.capture.format = ma_format_f32;
    config.capture.channels = 1;
    config.performanceProfile = ma_performance_profile_low_latency;
    config.dataCallback = dataCallback;
    config.notificationCallback = notificationCallback;
    config.pUserData = this;
    config.wasapi.noAutoStreamRouting = MA_TRUE;
    if (!initDevice(config, device.get(), kTag)) {
        MYAUDIO_LOGW(kTag, "Cannot open the %s device", duplex ? "duplex" : "playback");
        return false;
    }
    mDevice = device.release();
    return true;
}

void DesktopEngine::closeDevice() {
    if (mDevice == nullptr) return;
    auto *device = static_cast<ma_device *>(mDevice);
    ma_device_uninit(device);
    delete device;
    mDevice = nullptr;
}

bool DesktopEngine::startPlayback(int /* outputDeviceId */, bool /* bluetooth */) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mDevice != nullptr || mMixLeft.empty()) return false;
    const PlaybackLoop::Range loop = mLoop.range();
    mPosition = loop.active() ? loop.start : 0;
    mSeekRequest = -1;
    mRecording = false;
    if (!openDevice(false)) return false;
    mActive = true;
    if (ma_device_start(static_cast<ma_device *>(mDevice)) != MA_SUCCESS) {
        mActive = false;
        closeDevice();
        return false;
    }
    return true;
}

bool DesktopEngine::startRecording(const std::string &path, const std::string &referencePath, int64_t countInFrames,
                                   int /* outputDeviceId */, int /* inputDeviceId */, bool /* handsFreeInput */,
                                   bool /* bluetooth */, InputChannel /* miniaudio mixes all channels */) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mDevice != nullptr) return false;
    mPosition = -std::max<int64_t>(0, countInFrames);
    mSeekRequest = -1;
    mInputPeak = 0.0f;
    mDroppedFrames = 0;
    if (!mWav.open(path, kSampleRate)) return false;
    mFifo = std::make_unique<SpscFifo>(static_cast<size_t>(kFifoSeconds * kSampleRate));
    mReferenceFifo.reset();
    if (!referencePath.empty() && !mMixLeft.empty() && mReferenceWav.open(referencePath, kSampleRate)) {
        mReferenceFifo = std::make_unique<SpscFifo>(static_cast<size_t>(kFifoSeconds * kSampleRate));
        mReferenceScratch.assign(kScratchFrames, 0.0f);
    }
    if (!openDevice(true)) {
        mWav.finish();
        mReferenceWav.finish();
        return false;
    }
    mRecording = true;
    mActive = true;
    mWriterRunning = true;
    mWriter = std::thread(&DesktopEngine::writerLoop, this);
    if (ma_device_start(static_cast<ma_device *>(mDevice)) != MA_SUCCESS) {
        mActive = false;
        closeDevice();
        mWriterRunning = false;
        mWriter.join();
        mWav.finish();
        mReferenceWav.finish();
        mRecording = false;
        return false;
    }
    return true;
}

std::vector<int> DesktopEngine::currentDeviceIds() {
    // Only the system default devices are used so far; a lost device ends the transport instead.
    return {0, 0};
}

int DesktopEngine::transportXruns() {
    return mDroppedFrames.load() > 0 ? 1 : 0;
}

int64_t DesktopEngine::stopTransport() {
    std::lock_guard<std::mutex> guard(mLock);
    mActive = false;
    closeDevice();
    if (!mRecording) return -1;
    mRecording = false;
    mWriterRunning = false;
    if (mWriter.joinable()) mWriter.join();
    const int64_t frames = mWav.finish();
    mReferenceWav.finish();
    mReferenceFifo.reset();
    if (mDroppedFrames > 0) {
        MYAUDIO_LOGW(kTag, "Dropped %lld input frames", static_cast<long long>(mDroppedFrames.load()));
    }
    return frames;
}

void DesktopEngine::onDeviceLost() {
    mActive.store(false, std::memory_order_relaxed);
}

void DesktopEngine::process(float *output, const float *input, uint32_t frames) {
    if (!mActive.load(std::memory_order_relaxed)) {
        std::fill_n(output, frames * kChannels, 0.0f);
        return;
    }
    if (input == nullptr) {
        mix(output, kChannels, static_cast<int>(frames), nullptr, true);
        if (mPosition.load(std::memory_order_relaxed) >= mixFrames()) mActive.store(false, std::memory_order_relaxed);
        return;
    }
    const bool captureReference = mReferenceFifo != nullptr && frames <= mReferenceScratch.size();
    mix(output, kChannels, static_cast<int>(frames), captureReference ? mReferenceScratch.data() : nullptr, false);
    if (captureReference && !mReferenceFifo->write(mReferenceScratch.data(), frames)) {
        mDroppedFrames.fetch_add(frames, std::memory_order_relaxed);
    }
    float peak = mInputPeak.load(std::memory_order_relaxed);
    for (uint32_t frame = 0; frame < frames; ++frame) peak = std::max(peak, std::fabs(input[frame]));
    mInputPeak.store(peak, std::memory_order_relaxed);
    if (!mFifo->write(input, frames)) mDroppedFrames.fetch_add(frames, std::memory_order_relaxed);
}

// Same rendering as MultitrackEngine::mix on Android: mix, optional practice loop and the click.
void DesktopEngine::mix(float *output, int channels, int numFrames, float *mixOnly, bool looping) {
    const int64_t seek = mSeekRequest.exchange(-1, std::memory_order_relaxed);
    int64_t position = seek >= 0 ? seek : mPosition.load(std::memory_order_relaxed);
    const int64_t length = mixFrames();
    const bool metronome = mMetronomeEnabled.load(std::memory_order_relaxed);
    const ClickGrid grid(mMetronomeBpm.load(std::memory_order_relaxed), kSampleRate);
    const float metronomeGain = mMetronomeGain.load(std::memory_order_relaxed);
    const PlaybackLoop::Range loop = looping ? mLoop.range() : PlaybackLoop::Range{0, -1};
    for (int frame = 0; frame < numFrames; ++frame) {
        position = loop.wrap(position);
        const int64_t projectFrame = position++;
        const bool inMix = projectFrame >= 0 && projectFrame < length;
        float left = inMix ? mMixLeft[projectFrame] : 0.0f;
        float right = inMix ? mMixRight[projectFrame] : 0.0f;
        if (mixOnly != nullptr) mixOnly[frame] = 0.5f * (left + right);
        if (metronome) {
            const int64_t beat = grid.beatAt(projectFrame);
            const int64_t offset = projectFrame - grid.beatStart(beat);
            const std::vector<float> &click = ClickGrid::click(ClickGrid::isAccent(beat));
            if (offset >= 0 && offset < static_cast<int64_t>(click.size())) {
                const float tick = metronomeGain * click[offset];
                left = std::clamp(left + tick, -1.0f, 1.0f);
                right = std::clamp(right + tick, -1.0f, 1.0f);
            }
        }
        float *out = output + frame * channels;
        out[0] = left;
        out[1] = right;
    }
    mPosition.store(loop.wrap(position), std::memory_order_relaxed);
}

void DesktopEngine::writerLoop() {
    std::vector<float> chunk(kWriterChunkFrames);
    std::vector<int16_t> pcm(kWriterChunkFrames);
    auto drain = [&](SpscFifo *fifo, WavWriter &wav) {
        if (fifo == nullptr) return size_t{0};
        const size_t frames = fifo->read(chunk.data(), chunk.size());
        for (size_t i = 0; i < frames; ++i) pcm[i] = toPcm16(chunk[i]);
        if (frames > 0) wav.write(pcm.data(), frames);
        return frames;
    };
    while (true) {
        const bool running = mWriterRunning.load(std::memory_order_acquire);
        const size_t written = drain(mFifo.get(), mWav) + drain(mReferenceFifo.get(), mReferenceWav);
        if (written > 0) continue;
        if (!running) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

}  // namespace myaudio
