#include "LiveChain.h"

#include <algorithm>
#include <cmath>
#include <limits>

#include "AirwindowsEffects.h"
#include "AmpCabinet.h"
#include "Log.h"
#include "Loudness.h"
#include "Reverb.h"
#include "Tone.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioLive";
std::atomic<int> gBufferSetting{0};

/** Pink-ish noise at roughly -18 dBFS: the stand-in signal for estimating level matching live. */
std::vector<float> testSignal(int sampleRate) {
    std::vector<float> samples(static_cast<size_t>(sampleRate * 4));
    uint32_t state = 0x12345678u;
    float b0 = 0.0f, b1 = 0.0f, b2 = 0.0f;
    for (float &sample : samples) {
        state = state * 1664525u + 1013904223u;
        const float white = static_cast<float>(state >> 8) / static_cast<float>(1u << 24) * 2.0f - 1.0f;
        // Paul Kellet's economy pink filter.
        b0 = 0.99765f * b0 + white * 0.0990460f;
        b1 = 0.96300f * b1 + white * 0.2965164f;
        b2 = 0.57000f * b2 + white * 1.0526913f;
        sample = 0.05f * (b0 + b1 + b2 + white * 0.1848f);
    }
    return samples;
}

/**
 * Level matching cannot measure a take that is still being played, so the gain that brings the effect's
 * output back to its input loudness is estimated once on the test signal.
 */
float estimateLevelMatch(int index, const std::vector<float> &values, int sampleRate) {
    static const std::vector<float> signal = testSignal(48000);
    std::vector<float> left = signal;
    std::vector<float> right = signal;
    const double before = integratedLoudnessStreamed(left, right);
    if (!applyAirwindows(index, values, left, right, sampleRate)) return 1.0f;
    const double after = integratedLoudnessStreamed(left, right);
    if (!std::isfinite(before) || !std::isfinite(after)) return 1.0f;
    const double correctionDb = std::clamp(before - after, -24.0, 24.0);
    return static_cast<float>(std::pow(10.0, correctionDb / 20.0));
}

/** Level matching of an amp model or cabinet, estimated like [estimateLevelMatch] on its own instance. */
float estimateToneLevelMatch(ToneKind kind, const std::vector<float> &p, bool stereo, int sampleRate) {
    static const std::vector<float> signal = testSignal(48000);
    auto processor = ToneProcessor::create(kind, static_cast<int>(p[0]), kind == ToneKind::AmpModel ? p[1] : 0.0f,
                                           kind == ToneKind::AmpModel ? p[2] : 0.0f, stereo, sampleRate);
    if (!processor) return 1.0f;
    std::vector<float> left = signal;
    std::vector<float> right = signal;
    const double before = integratedLoudnessStreamed(left, right);
    processor->process(left.data(), stereo ? right.data() : nullptr, static_cast<int>(left.size()));
    const double after = integratedLoudnessStreamed(left, stereo ? right : left);
    if (!std::isfinite(before) || !std::isfinite(after)) return 1.0f;
    return static_cast<float>(std::pow(10.0, std::clamp(before - after, -24.0, 24.0) / 20.0));
}

}  // namespace

class LiveChain::Stage {
public:
    virtual ~Stage() = default;
    virtual void process(float *left, float *right, int frames) = 0;
};

namespace {

/** "EQ (My Audio)": EQ, then the linked compressor, as in applyEffectChain. */
class ToneStage : public LiveChain::Stage {
public:
    ToneStage(const std::vector<float> &p, int sampleRate)
        : mEq(!std::isnan(p[0])), mCompress(!std::isnan(p[3])),
          mLeft({mEq ? p[0] : 0.0f, p[1], p[2]}, sampleRate), mRight({mEq ? p[0] : 0.0f, p[1], p[2]}, sampleRate),
          mCompressor(mCompress ? p[3] : 0.0f, sampleRate) {}

    void process(float *left, float *right, int frames) override {
        for (int i = 0; i < frames; ++i) {
            if (mEq) {
                left[i] = mLeft.process(left[i]);
                right[i] = mRight.process(right[i]);
            }
            if (mCompress) {
                // A mono signal has equal channels, so this is also the mono detector of the offline mix.
                const float gain = mCompressor.gain(std::max(std::fabs(left[i]), std::fabs(right[i])));
                left[i] *= gain;
                right[i] *= gain;
            }
        }
    }

private:
    bool mEq;
    bool mCompress;
    EqFilter mLeft;
    EqFilter mRight;
    Compressor mCompressor;
};

/** "Hall (My Audio)": a mono input feeds one network, a stereo input keeps a second one for the right channel. */
class ReverbStage : public LiveChain::Stage {
public:
    ReverbStage(const ReverbParameters &parameters, bool stereoInput, int sampleRate)
        : mStereoInput(stereoInput), mLeft(parameters, sampleRate), mRight(parameters, sampleRate) {
        mActive = fdn::reverbGains(parameters, sampleRate, mDryGain, mWetGain);
    }

    void process(float *left, float *right, int frames) override {
        if (!mActive) {
            if (!mStereoInput) std::copy_n(left, frames, right);
            return;
        }
        for (int i = 0; i < frames; ++i) {
            const float dry = left[i];
            const float dryRight = mStereoInput ? right[i] : dry;
            float wetLeft = 0.0f;
            float wetRight = 0.0f;
            mLeft.process(dry, wetLeft, wetRight);
            if (mStereoInput) {
                float unused = 0.0f;
                mRight.process(dryRight, unused, wetRight);
            }
            left[i] = mDryGain * dry + mWetGain * wetLeft;
            right[i] = mDryGain * dryRight + mWetGain * wetRight;
        }
    }

private:
    bool mStereoInput;
    bool mActive = false;
    float mDryGain = 1.0f;
    float mWetGain = 0.0f;
    fdn::Reverb mLeft;
    fdn::Reverb mRight;
};

class AirwindowsStage : public LiveChain::Stage {
public:
    AirwindowsStage(std::unique_ptr<AirwindowsProcessor> processor, float gain)
        : mProcessor(std::move(processor)), mGain(gain) {}

    void process(float *left, float *right, int frames) override {
        mProcessor->process(left, right, frames);
        if (mGain == 1.0f) return;
        for (int i = 0; i < frames; ++i) {
            left[i] *= mGain;
            right[i] *= mGain;
        }
    }

private:
    std::unique_ptr<AirwindowsProcessor> mProcessor;
    float mGain;
};

/** Imported amp model or cabinet; a mono signal is processed once and copied to the right channel. */
class ToneModelStage : public LiveChain::Stage {
public:
    ToneModelStage(std::unique_ptr<ToneProcessor> processor, bool stereo, float gain)
        : mProcessor(std::move(processor)), mStereo(stereo), mGain(gain) {}

    void process(float *left, float *right, int frames) override {
        mProcessor->process(left, mStereo ? right : nullptr, frames);
        if (!mStereo) std::copy_n(left, frames, right);
        if (mGain == 1.0f) return;
        for (int i = 0; i < frames; ++i) {
            left[i] *= mGain;
            right[i] *= mGain;
        }
    }

private:
    std::unique_ptr<ToneProcessor> mProcessor;
    bool mStereo;
    float mGain;
};

}  // namespace

LiveChain::LiveChain(const std::vector<EffectSlot> &chain, float gain, float pan, bool silent, int sampleRate)
    : mSilent(silent) {
    if (silent) return;
    // Mirrors applyEffectChain: the microphone is mono until a reverb or Airwindows effect makes it stereo.
    bool stereo = false;
    for (const EffectSlot &slot : chain) {
        const std::vector<float> &p = slot.parameters;
        switch (slot.type) {
            case EffectType::Tone:
                mStages.push_back(std::make_unique<ToneStage>(p, sampleRate));
                break;
            case EffectType::Reverb:
                mStages.push_back(std::make_unique<ReverbStage>(ReverbParameters{p[0], p[1], p[2], p[3]}, stereo, sampleRate));
                stereo = true;
                break;
            case EffectType::Airwindows:
            case EffectType::AirwindowsLevelMatched: {
                const auto index = static_cast<int>(p[0]);
                const std::vector<float> values(p.begin() + 1, p.end());
                auto processor = AirwindowsProcessor::create(index, values, sampleRate);
                if (!processor) {
                    MYAUDIO_LOGW(kTag, "Skipping unknown Airwindows effect %d", index);
                    break;
                }
                const float match = slot.type == EffectType::AirwindowsLevelMatched
                                    ? estimateLevelMatch(index, values, sampleRate) : 1.0f;
                mStages.push_back(std::make_unique<AirwindowsStage>(std::move(processor), match));
                stereo = true;
                break;
            }
            case EffectType::AmpModel:
            case EffectType::AmpModelLevelMatched:
            case EffectType::Cabinet:
            case EffectType::CabinetLevelMatched: {
                const bool cabinet = slot.type == EffectType::Cabinet || slot.type == EffectType::CabinetLevelMatched;
                const ToneKind kind = cabinet ? ToneKind::Cabinet : ToneKind::AmpModel;
                auto processor = ToneProcessor::create(kind, static_cast<int>(p[0]), cabinet ? 0.0f : p[1],
                                                       cabinet ? 0.0f : p[2], stereo, sampleRate);
                if (!processor) {
                    MYAUDIO_LOGW(kTag, "Skipping missing tone %d", static_cast<int>(p[0]));
                    break;
                }
                const bool match = slot.type == EffectType::AmpModelLevelMatched || slot.type == EffectType::CabinetLevelMatched;
                const float gain = match ? estimateToneLevelMatch(kind, p, stereo, sampleRate) : 1.0f;
                mStages.push_back(std::make_unique<ToneModelStage>(std::move(processor), stereo, gain));
                break;
            }
        }
    }
    // The microphone is a mono source, so the track's pan applies as for a mono take.
    panGains(pan, mGainLeft, mGainRight);
    mGainLeft *= gain;
    mGainRight *= gain;
}

LiveChain::~LiveChain() = default;

void LiveChain::process(const float *input, float *left, float *right, int frames) {
    if (mSilent) {
        std::fill_n(left, frames, 0.0f);
        std::fill_n(right, frames, 0.0f);
        return;
    }
    std::copy_n(input, frames, left);
    std::copy_n(input, frames, right);
    for (const auto &stage : mStages) stage->process(left, right, frames);
    for (int i = 0; i < frames; ++i) {
        left[i] *= mGainLeft;
        right[i] *= mGainRight;
    }
}

LiveMonitor::LiveMonitor()
    : mLeft(LiveChain::kMaxFrames), mRight(LiveChain::kMaxFrames), mFadeLeft(LiveChain::kMaxFrames),
      mFadeRight(LiveChain::kMaxFrames) {
    for (auto &slot : mRetired) slot.store(nullptr, std::memory_order_relaxed);
}

LiveMonitor::~LiveMonitor() {
    collect();
    delete mPending.exchange(nullptr);
    delete mActive;
    delete mFading;
}

void LiveMonitor::setChain(std::unique_ptr<LiveChain> chain) {
    collect();
    // A chain the callback has not picked up yet was never heard; whoever exchanges it out owns it.
    delete mPending.exchange(chain.release(), std::memory_order_acq_rel);
}

void LiveMonitor::collect() {
    for (auto &slot : mRetired) delete slot.exchange(nullptr, std::memory_order_acq_rel);
}

void LiveMonitor::retire(LiveChain *chain) {
    for (auto &slot : mRetired) {
        LiveChain *expected = nullptr;
        if (slot.compare_exchange_strong(expected, chain, std::memory_order_acq_rel)) return;
    }
    // Unreachable while the control thread collects on every change; keeping it alive beats freeing here.
}

void LiveMonitor::process(const float *input, float *output, int channels, int frames) {
    // Take a new chain only when the one it replaces can be retired without allocating.
    if (mPending.load(std::memory_order_acquire) != nullptr) {
        const bool slotFree = std::any_of(mRetired.begin(), mRetired.end(),
                                          [](const auto &slot) { return slot.load(std::memory_order_acquire) == nullptr; });
        if (slotFree || mFading == nullptr) {
            if (LiveChain *next = mPending.exchange(nullptr, std::memory_order_acq_rel)) {
                if (mFading != nullptr) retire(mFading);
                mFading = mActive;
                mActive = next;
                mFadePosition = 0;
            }
        }
    }
    if (mActive == nullptr || (mActive->silent() && (mFading == nullptr || mFading->silent()))) {
        if (mFading != nullptr) {
            retire(mFading);
            mFading = nullptr;
        }
        return;
    }
    for (int start = 0; start < frames; start += LiveChain::kMaxFrames) {
        const int count = std::min(LiveChain::kMaxFrames, frames - start);
        mActive->process(input + start, mLeft.data(), mRight.data(), count);
        if (mFading != nullptr) {
            mFading->process(input + start, mFadeLeft.data(), mFadeRight.data(), count);
            for (int i = 0; i < count; ++i) {
                const float in = std::min(1.0f, static_cast<float>(mFadePosition + i) / kFadeFrames);
                mLeft[i] = in * mLeft[i] + (1.0f - in) * mFadeLeft[i];
                mRight[i] = in * mRight[i] + (1.0f - in) * mFadeRight[i];
            }
            mFadePosition += count;
            if (mFadePosition >= kFadeFrames) {
                retire(mFading);
                mFading = nullptr;
            }
        }
        float *out = output + static_cast<size_t>(start) * channels;
        for (int i = 0; i < count; ++i) {
            if (channels == 1) {
                out[i] = std::clamp(out[i] + 0.5f * (mLeft[i] + mRight[i]), -1.0f, 1.0f);
            } else {
                out[i * channels] = std::clamp(out[i * channels] + mLeft[i], -1.0f, 1.0f);
                out[i * channels + 1] = std::clamp(out[i * channels + 1] + mRight[i], -1.0f, 1.0f);
            }
        }
    }
}

void setBufferSetting(int setting) {
    gBufferSetting.store(setting == 1 || setting == 2 || setting == 4 ? setting : 0, std::memory_order_relaxed);
}

int bufferSetting() { return gBufferSetting.load(std::memory_order_relaxed); }

}  // namespace myaudio
