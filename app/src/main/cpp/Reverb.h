#pragma once

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <vector>

#include "TrackProcessing.h"

namespace myaudio {

/**
 * The algorithmic reverb of "Hall (My Audio)" as a sample-by-sample processor, shared by the offline
 * mix (applyReverb) and the real-time monitoring chain (LiveChain), so both sound the same.
 */
namespace fdn {

/** Fixed-length delay; read() returns the sample written [length] samples ago. */
class DelayLine {
public:
    explicit DelayLine(size_t length) : mBuffer(std::max<size_t>(length, 1), 0.0f) {}
    float read() const { return mBuffer[mIndex]; }
    void write(float value) {
        mBuffer[mIndex] = value;
        mIndex = (mIndex + 1) % mBuffer.size();
    }
    /** Sample written [delay] samples ago, 1 = the latest write. */
    float tap(size_t delay) const { return mBuffer[(mIndex + mBuffer.size() - delay) % mBuffer.size()]; }

private:
    std::vector<float> mBuffer;
    size_t mIndex = 0;
};

/** Schroeder allpass: H(z) = (z^-M - g) / (1 - g z^-M). */
inline float allpass(DelayLine &line, float input, float gain) {
    const float delayed = line.read();
    const float v = input + gain * delayed;
    line.write(v);
    return delayed - gain * v;
}

inline int nextPrime(int value) {
    for (int candidate = std::max(value, 2);; ++candidate) {
        bool prime = true;
        for (int divisor = 2; divisor * divisor <= candidate && prime; ++divisor) prime = candidate % divisor != 0;
        if (prime) return candidate;
    }
}

constexpr int kLines = 16;
// FDN delay lengths in ms for a medium room; scaled with the room size and rounded to primes so
// the lines never share a period.
constexpr double kLineMs[kLines] = {23.1, 26.3, 29.7, 32.9, 36.1, 39.7, 43.3, 46.9,
                                    50.9, 54.7, 58.3, 62.9, 66.7, 71.3, 75.1, 79.9};
// Input diffusion after Dattorro (samples at 29.761 kHz) and its gains.
constexpr double kDiffuserSamples[] = {142, 107, 379, 277};
constexpr float kDiffuserGains[] = {0.75f, 0.75f, 0.625f, 0.625f};
// Early reflections: times in ms for a medium room and their gains (alternating sign).
constexpr double kReflectionMs[] = {7.1, 11.3, 16.7, 21.2, 27.9, 33.4, 41.7, 52.3};
constexpr float kReflectionGains[] = {0.84f, -0.72f, 0.65f, -0.55f, 0.47f, -0.40f, 0.32f, -0.25f};

class Reverb {
public:
    Reverb(const ReverbParameters &parameters, int sampleRate) : mPreDelay(1), mReflections(1) {
        const double seconds = std::clamp(parameters.seconds, 0.1f, 20.0f);
        // Bigger rooms: longer lines and later reflections; the decay time sets the feedback.
        const double size = std::clamp(std::sqrt(seconds / 1.5), 0.5, 2.0);
        const double highSeconds = seconds * (1.0 - 0.8 * std::clamp(parameters.damping, 0.0f, 1.0f));

        mPreDelaySamples = static_cast<size_t>(std::max(0.0f, parameters.preDelayMs) * sampleRate / 1000.0f);
        mPreDelay = DelayLine(mPreDelaySamples);
        for (int i = 0; i < 4; ++i) {
            mDiffusers.emplace_back(static_cast<size_t>(kDiffuserSamples[i] * sampleRate / 29761.0));
        }
        size_t longestReflection = 1;
        for (double ms : kReflectionMs) {
            const auto delay = static_cast<size_t>(ms * size * sampleRate / 1000.0);
            mReflectionDelays.push_back(delay);
            longestReflection = std::max(longestReflection, delay + 1);
        }
        mReflections = DelayLine(longestReflection);
        for (int i = 0; i < kLines; ++i) {
            const int length = nextPrime(static_cast<int>(kLineMs[i] * size * sampleRate / 1000.0));
            mLines.emplace_back(static_cast<size_t>(length));
            // One-pole absorption filter: gain gLow at DC and gHigh at Nyquist give the per-pass
            // attenuation for a 60 dB decay after [seconds] (lows) and [highSeconds] (highs).
            const double low = std::pow(10.0, -3.0 * length / (seconds * sampleRate));
            const double high = std::pow(10.0, -3.0 * length / (highSeconds * sampleRate));
            const double pole = (low - high) / (low + high);
            mPoles[i] = static_cast<float>(pole);
            mGains[i] = static_cast<float>(low * (1.0 - pole));
        }
    }

    /**
     * Wet signal for one input sample. Left and right tap the reflections and the delay lines
     * with orthogonal sign patterns, so they are decorrelated but sum to a full mono reverb.
     */
    void process(float input, float &left, float &right) {
        float delayed = input;
        if (mPreDelaySamples > 0) {
            delayed = mPreDelay.read();
            mPreDelay.write(input);
        }
        mReflections.write(delayed);
        float earlyLeft = 0.0f;
        float earlyRight = 0.0f;
        for (size_t i = 0; i < mReflectionDelays.size(); ++i) {
            const float reflection = kReflectionGains[i] * mReflections.tap(mReflectionDelays[i] + 1);
            (i % 2 == 0 ? earlyLeft : earlyRight) += reflection;
        }
        float diffused = delayed;
        for (size_t i = 0; i < mDiffusers.size(); ++i) diffused = allpass(mDiffusers[i], diffused, kDiffuserGains[i]);

        float outputs[kLines];
        float sum = 0.0f;
        float lateLeft = 0.0f;
        float lateRight = 0.0f;
        for (int i = 0; i < kLines; ++i) {
            mFilterStates[i] = mGains[i] * mLines[i].read() + mPoles[i] * mFilterStates[i];
            outputs[i] = mFilterStates[i];
            sum += outputs[i];
            lateLeft += (i % 2 == 0 ? 0.25f : -0.25f) * outputs[i];
            lateRight += ((i / 2) % 2 == 0 ? 0.25f : -0.25f) * outputs[i];
        }
        // Householder feedback matrix (I - 2/N * 11^T): lossless, maximally mixing, O(N).
        const float householder = 2.0f / kLines * sum;
        for (int i = 0; i < kLines; ++i) {
            const float feed = (i % 3 == 0 ? -0.25f : 0.25f) * diffused;
            mLines[i].write(outputs[i] - householder + feed);
        }
        left = 0.5f * earlyLeft + lateLeft;
        right = 0.5f * earlyRight + lateRight;
    }

private:
    size_t mPreDelaySamples = 0;
    DelayLine mPreDelay;
    std::vector<DelayLine> mDiffusers;
    DelayLine mReflections;
    std::vector<size_t> mReflectionDelays;
    std::vector<DelayLine> mLines;
    float mPoles[kLines] = {};
    float mGains[kLines] = {};
    float mFilterStates[kLines] = {};
};

/** How long the reverb rings after the input ends; also the length of the energy probe. */
inline float tailSeconds(const ReverbParameters &parameters) {
    return std::min(parameters.seconds * 1.1f + parameters.preDelayMs / 1000.0f, 12.0f);
}

/**
 * Dry and wet gains of the reverb: an equal-power crossfade (mix 0 = dry, 0.5 = equal energy, 1 = wet
 * only) with the wet path normalised to unit impulse-response energy per channel, so "mix" means the
 * same balance for a small chamber and a cathedral. False when the reverb adds nothing.
 */
inline bool reverbGains(const ReverbParameters &parameters, int sampleRate, float &dryGain, float &wetGain) {
    if (parameters.seconds <= 0.0f || parameters.mix <= 0.0f) return false;
    const auto tail = static_cast<size_t>(tailSeconds(parameters) * sampleRate);
    Reverb probe(parameters, sampleRate);
    double energy = 0.0;
    for (size_t n = 0; n < tail; ++n) {
        float wetLeft = 0.0f;
        float wetRight = 0.0f;
        probe.process(n == 0 ? 1.0f : 0.0f, wetLeft, wetRight);
        energy += 0.5 * (static_cast<double>(wetLeft) * wetLeft + static_cast<double>(wetRight) * wetRight);
    }
    if (energy <= 0.0) return false;
    const float mix = std::clamp(parameters.mix, 0.0f, 1.0f);
    dryGain = static_cast<float>(std::cos(mix * M_PI / 2));
    wetGain = static_cast<float>(std::sin(mix * M_PI / 2) / std::sqrt(energy));
    return true;
}

}  // namespace fdn

}  // namespace myaudio
