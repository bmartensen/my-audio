#pragma once

#include <cstdint>
#include <mutex>
#include <vector>

#include "ProjectMix.h"

namespace myaudio {

/**
 * Auditioning effects: holds a short dry excerpt (a take as the mix uses it, the project sum before
 * the master chain, or the bundled sample) and renders it through a chain as a seamless loop.
 * Rendering is offline and quick (a few seconds of audio), so the caller re-renders after every
 * change and the result feels live. Shared by Android and desktop; playback is done by the caller.
 */
class EffectPreview {
public:
    static EffectPreview &instance();

    /** [frames] of [source] from project frame [startFrame], as the track's chain receives it. */
    bool setTrack(const TrackSource &source, int64_t startFrame, int64_t frames);

    /** [frames] of the summed [sources] from [startFrame], as the master chain receives it; the master stage follows. */
    bool setMix(const std::vector<TrackSource> &sources, int64_t startFrame, int64_t frames);

    /** A clip of interleaved samples with [channels] (1 or 2), at the project sample rate. */
    bool setSamples(const float *samples, size_t frames, int channels);

    /**
     * The excerpt through [chain] as interleaved stereo of the excerpt's length. Tails (reverb,
     * delays) are folded back onto the start, so the loop sounds like a continuous repetition.
     * The listening level is the same for every chain on the same excerpt, so A/B stays fair.
     */
    std::vector<float> render(const std::vector<EffectSlot> &chain);

private:
    void prepareLocked();

    std::mutex mLock;
    std::vector<float> mLeft;
    std::vector<float> mRight;  // empty for a mono excerpt
    bool mMaster = false;        // the master stage follows the chain
    float mListenGain = 1.0f;    // brings the excerpt to a comfortable level after the chain
};

}  // namespace myaudio
