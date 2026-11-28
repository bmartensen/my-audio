#pragma once

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "InputChannel.h"
#include "LiveChain.h"
#include "PlaybackLoop.h"
#include "ProjectMix.h"
#include "Wav.h"

namespace myaudio {

/** Lock-free queue for one producer (audio callback) and one consumer (writer thread). */
class SpscFifo {
public:
    explicit SpscFifo(size_t capacity) : mBuffer(capacity + 1) {}

    /** Writes all [count] samples or none; false when full. */
    bool write(const float *samples, size_t count) {
        const size_t head = mHead.load(std::memory_order_relaxed);
        const size_t tail = mTail.load(std::memory_order_acquire);
        const size_t free = (tail + mBuffer.size() - head - 1) % mBuffer.size();
        if (count > free) return false;
        for (size_t i = 0; i < count; ++i) mBuffer[(head + i) % mBuffer.size()] = samples[i];
        mHead.store((head + count) % mBuffer.size(), std::memory_order_release);
        return true;
    }

    /** Reads up to [count] samples; returns how many were read. */
    size_t read(float *samples, size_t count) {
        const size_t tail = mTail.load(std::memory_order_relaxed);
        const size_t head = mHead.load(std::memory_order_acquire);
        const size_t available = (head + mBuffer.size() - tail) % mBuffer.size();
        const size_t n = std::min(count, available);
        for (size_t i = 0; i < n; ++i) samples[i] = mBuffer[(tail + i) % mBuffer.size()];
        mTail.store((tail + n) % mBuffer.size(), std::memory_order_release);
        return n;
    }

private:
    std::vector<float> mBuffer;
    std::atomic<size_t> mHead{0};
    std::atomic<size_t> mTail{0};
};

/**
 * Desktop transport (Windows WASAPI, Linux PulseAudio/ALSA via miniaudio) with the interface of
 * the Android MultitrackEngine, so the shared JNI bridge and Kotlin code work unchanged. Playback
 * and recording run in one duplex device, like the calibration, so the measured latency applies.
 * Device ids: 0 = the system default device (the only choice so far).
 */
class DesktopEngine {
public:
    static constexpr int kSampleRate = kProjectSampleRate;

    static DesktopEngine &instance();

    bool setTracks(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master);
    bool exportStems(const std::vector<TrackSource> &sources, int64_t projectFrames,
                     const std::vector<std::string> &targets);
    bool startPlayback(int64_t startFrame, int outputDeviceId, bool bluetooth);
    bool startRecording(const std::string &path, const std::string &referencePath, int64_t startFrame, int64_t countInFrames,
                        int outputDeviceId, int inputDeviceId, bool handsFreeInput, bool bluetooth,
                        InputChannel inputChannel);
    /** Live audition: microphone through the monitor chain to the output, nothing played or recorded. */
    bool startMonitoring(int outputDeviceId, int inputDeviceId, bool handsFreeInput, bool bluetooth,
                         InputChannel inputChannel);
    /** The chain the microphone is monitored through while recording or in live audition; any time. */
    void setMonitorChain(std::unique_ptr<LiveChain> chain) { mMonitor.setChain(std::move(chain)); }
    std::vector<int> currentDeviceIds();
    int transportXruns();
    int64_t stopTransport();
    int64_t positionFrames() const { return mPosition.load(std::memory_order_relaxed); }
    bool isActive() const { return mActive.load(std::memory_order_relaxed); }
    float takeInputPeak() { return mInputPeak.exchange(0.0f, std::memory_order_relaxed); }
    void setMetronome(bool enabled, float bpm, float volume);
    std::vector<float> renderPlayback();
    void seekPlayback(int64_t frame);
    void setLoop(int64_t start, int64_t end) { mLoop.set(start, end); }

    /** Audio thread entry; [input] is null for playback only. */
    void process(float *output, const float *input, uint32_t frames);
    /** The device stopped or was rerouted by the system; the transport ends. */
    void onDeviceLost();

private:
    DesktopEngine() = default;
    /** [inputChannel] other than Device opens the microphone in stereo and records that channel. */
    bool openDevice(bool duplex, InputChannel inputChannel = InputChannel::Device);
    void closeDevice();
    void mix(float *output, int channels, int numFrames, float *mixOnly, bool looping);
    void writerLoop();
    int64_t mixFrames() const { return static_cast<int64_t>(mMixLeft.size()); }

    std::mutex mLock;
    PlaybackLoop mLoop;
    std::vector<float> mMixLeft;
    std::vector<float> mMixRight;
    void *mDevice = nullptr;  // ma_device, owned
    bool mRecording = false;
    bool mMonitorOnly = false;  // live audition: no mix, no take
    InputChannel mInputChannel = InputChannel::Device;
    std::vector<float> mInputScratch;  // the chosen channel of a stereo microphone, as mono
    LiveMonitor mMonitor;

    std::atomic<int64_t> mPosition{0};
    std::atomic<int64_t> mSeekRequest{-1};
    std::atomic<bool> mActive{false};
    std::atomic<float> mInputPeak{0.0f};
    std::atomic<bool> mMetronomeEnabled{false};
    std::atomic<float> mMetronomeBpm{120.0f};
    std::atomic<float> mMetronomeGain{0.15f};

    std::unique_ptr<SpscFifo> mFifo;
    std::unique_ptr<SpscFifo> mReferenceFifo;  // null when nothing is played
    std::vector<float> mReferenceScratch;
    WavWriter mWav;
    WavWriter mReferenceWav;
    std::atomic<bool> mWriterRunning{false};
    std::atomic<int64_t> mDroppedFrames{0};
    std::thread mWriter;
};

}  // namespace myaudio
