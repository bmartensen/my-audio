#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>

#include "ProjectMix.h"

namespace myaudio {

/**
 * Real-time version of a track's effect chain for live monitoring: the microphone (mono) through the
 * same EQ/compressor, reverb and Airwindows processing as the offline mix, then the track's volume and
 * panorama. Built outside the audio thread (it allocates and measures); process() never allocates.
 *
 * Differences to the rendered mix: input clean-up and peak normalisation need the whole take and do not
 * apply; Airwindows level matching uses a gain estimated once on a test signal instead of the take.
 */
class LiveChain {
public:
    /** The longest block process() handles in one call; the monitor splits longer ones. */
    static constexpr int kMaxFrames = 1024;

    /** [silent]: outputs nothing (monitoring off). An empty [chain] passes the microphone through dry. */
    LiveChain(const std::vector<EffectSlot> &chain, float gain, float pan, bool silent, int sampleRate);
    ~LiveChain();

    bool silent() const { return mSilent; }

    /** [input] mono, [frames] <= kMaxFrames; writes the processed stereo signal to [left]/[right]. */
    void process(const float *input, float *left, float *right, int frames);

    class Stage;

private:
    std::vector<std::unique_ptr<Stage>> mStages;
    bool mSilent;
    float mGainLeft = 1.0f;
    float mGainRight = 1.0f;
};

/**
 * Hands live chains to the audio callback without locks and mixes the monitored microphone into the
 * output. A new chain fades in over a few milliseconds while the previous one fades out, so parameter
 * changes do not click. Replaced chains are deleted on the control thread, never in the callback.
 */
class LiveMonitor {
public:
    LiveMonitor();
    ~LiveMonitor();

    /** Control thread: the chain heard from the next callback block on. */
    void setChain(std::unique_ptr<LiveChain> chain);

    /**
     * Audio thread: processes [frames] of mono [input] and adds the result to the interleaved [output]
     * (stereo in its first two channels), clamped to full scale. Only reads [input]; the take is
     * written from the untouched input, never from this signal.
     */
    void process(const float *input, float *output, int channels, int frames);

private:
    void retire(LiveChain *chain);
    void collect();

    static constexpr int kRetired = 8;
    static constexpr int kFadeFrames = 960;  // 20 ms at 48 kHz
    std::atomic<LiveChain *> mPending{nullptr};
    std::array<std::atomic<LiveChain *>, kRetired> mRetired{};
    LiveChain *mActive = nullptr;
    LiveChain *mFading = nullptr;
    int mFadePosition = 0;
    std::vector<float> mLeft;
    std::vector<float> mRight;
    std::vector<float> mFadeLeft;
    std::vector<float> mFadeRight;
};

/**
 * Global buffer choice for every duplex stream (calibration, recording, live): 0 = the system's low-latency
 * default, otherwise 1 (small), 2 (medium) or 4 (large) device periods (Android bursts; desktop periods of
 * 128 frames). A changed choice needs its own calibration, because the round trip changes with it.
 */
void setBufferSetting(int setting);
int bufferSetting();

}  // namespace myaudio
