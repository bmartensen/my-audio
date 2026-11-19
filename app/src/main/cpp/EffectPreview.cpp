#include "EffectPreview.h"

#include <algorithm>
#include <chrono>
#include <cmath>

#include "Log.h"
#include "Loudness.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioPreview";
constexpr int kRate = kProjectSampleRate;
// Silence after the excerpt, so effects that ring on (delays, Airwindows reverbs) produce their tail.
constexpr int64_t kTailFrames = 3 * kRate;
// Short fades at the excerpt's cut edges avoid a click where the loop wraps.
constexpr int64_t kFadeFrames = kRate / 100;
// Listening level of the preview and its limits.
constexpr double kListenLufs = -18.0;
constexpr double kMaxListenGainDb = 24.0;
// The master stage ends at -14 LUFS; this brings it to the same listening level as the other previews.
constexpr float kMasterListenGain = 0.631f;  // -4 dB

/** [frames] of [source] from index [first]; outside the source is silence. */
std::vector<float> excerpt(const std::vector<float> &source, int64_t first, int64_t frames) {
    std::vector<float> out(static_cast<size_t>(std::max<int64_t>(frames, 0)), 0.0f);
    for (int64_t i = 0; i < frames; ++i) {
        const int64_t index = first + i;
        if (index >= 0 && index < static_cast<int64_t>(source.size())) out[i] = source[index];
    }
    return out;
}

void fadeEdges(std::vector<float> &channel) {
    const auto n = static_cast<int64_t>(channel.size());
    const int64_t fade = std::min(kFadeFrames, n / 2);
    for (int64_t i = 0; i < fade; ++i) {
        const float gain = static_cast<float>(i) / static_cast<float>(fade);
        channel[i] *= gain;
        channel[n - 1 - i] *= gain;
    }
}

}  // namespace

EffectPreview &EffectPreview::instance() {
    static EffectPreview preview;
    return preview;
}

bool EffectPreview::setTrack(const TrackSource &source, int64_t startFrame, int64_t frames) {
    std::vector<float> left;
    std::vector<float> right;
    if (frames <= 0 || !loadProcessedTake(source, left, right)) return false;
    const int64_t first = startFrame - source.offsetFrames;
    std::lock_guard<std::mutex> guard(mLock);
    mLeft = excerpt(left, first, frames);
    mRight = right.empty() ? std::vector<float>{} : excerpt(right, first, frames);
    mMaster = false;
    prepareLocked();
    return true;
}

bool EffectPreview::setMix(const std::vector<TrackSource> &sources, int64_t startFrame, int64_t frames) {
    std::vector<float> left;
    std::vector<float> right;
    if (frames <= 0) return false;
    mixTracks(sources, left, right);
    if (left.empty()) return false;
    std::lock_guard<std::mutex> guard(mLock);
    mLeft = excerpt(left, startFrame, frames);
    mRight = excerpt(right, startFrame, frames);
    mMaster = true;
    prepareLocked();
    return true;
}

bool EffectPreview::setSamples(const float *samples, size_t frames, int channels) {
    if (frames == 0 || (channels != 1 && channels != 2)) return false;
    std::lock_guard<std::mutex> guard(mLock);
    mLeft.assign(frames, 0.0f);
    mRight.assign(channels == 2 ? frames : 0, 0.0f);
    for (size_t i = 0; i < frames; ++i) {
        mLeft[i] = samples[i * channels];
        if (channels == 2) mRight[i] = samples[i * channels + 1];
    }
    mMaster = false;
    prepareLocked();
    return true;
}

void EffectPreview::prepareLocked() {
    fadeEdges(mLeft);
    if (!mRight.empty()) fadeEdges(mRight);
    // The chain must hear the real level (a drive or compressor reacts to it); the listening gain
    // is applied afterwards and measured on the dry excerpt, so it is the same for A and B.
    const double lufs = integratedLoudnessStreamed(mLeft, mRight.empty() ? mLeft : mRight);
    mListenGain = std::isfinite(lufs)
                  ? static_cast<float>(std::pow(10.0, std::clamp(kListenLufs - lufs, -kMaxListenGainDb, kMaxListenGainDb) / 20.0))
                  : 1.0f;
}

std::vector<float> EffectPreview::render(const std::vector<EffectSlot> &chain) {
    const auto start = std::chrono::steady_clock::now();
    std::vector<float> left;
    std::vector<float> right;
    bool stereo;
    bool master;
    float listenGain;
    {
        std::lock_guard<std::mutex> guard(mLock);
        if (mLeft.empty()) return {};
        left = mLeft;
        right = mRight;
        stereo = !mRight.empty();
        master = mMaster;
        listenGain = mListenGain;
    }
    const size_t frames = left.size();
    left.resize(frames + kTailFrames, 0.0f);
    if (stereo) right.resize(frames + kTailFrames, 0.0f);

    applyEffectChain(chain, left, right, stereo, kRate);
    if (!stereo) right = left;
    right.resize(left.size(), 0.0f);
    if (master) {
        applyMasterStage(left, right);
        listenGain = kMasterListenGain;
    }

    // Everything after the excerpt rings into the next round of the loop.
    std::vector<float> loop(frames * 2, 0.0f);
    for (size_t i = 0; i < left.size(); ++i) {
        const size_t frame = i % frames;
        loop[frame * 2] += left[i] * listenGain;
        loop[frame * 2 + 1] += right[i] * listenGain;
    }
    // Safety for extreme settings only: never clip the speaker.
    float peak = 0.0f;
    for (float sample : loop) peak = std::max(peak, std::fabs(sample));
    if (peak > 0.99f) {
        const float scale = 0.99f / peak;
        for (float &sample : loop) sample *= scale;
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start);
    MYAUDIO_LOGI(kTag, "Rendered %.1f s preview through %zu effects in %lld ms", static_cast<double>(frames) / kRate,
                 chain.size(), static_cast<long long>(elapsed.count()));
    return loop;
}

}  // namespace myaudio
