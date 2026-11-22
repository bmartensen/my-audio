#pragma once

#include <algorithm>
#include <cmath>
#include <vector>

namespace myaudio {

/** Second-order IIR section (transposed direct form II); designs after the RBJ audio EQ cookbook. */
class Biquad {
public:
    Biquad(double b0, double b1, double b2, double a1, double a2) : mB0(b0), mB1(b1), mB2(b2), mA1(a1), mA2(a2) {}

    static Biquad lowShelf(double frequency, double gainDb, int sampleRate) {
        const double a = std::pow(10.0, gainDb / 40.0);
        const double w = 2.0 * M_PI * frequency / sampleRate;
        const double c = std::cos(w);
        const double beta = 2.0 * std::sqrt(a) * std::sin(w) / 2.0 * std::sqrt(2.0);  // shelf slope S = 1
        return normalized(a * ((a + 1) - (a - 1) * c + beta), 2 * a * ((a - 1) - (a + 1) * c),
                          a * ((a + 1) - (a - 1) * c - beta), (a + 1) + (a - 1) * c + beta,
                          -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - beta);
    }

    static Biquad highShelf(double frequency, double gainDb, int sampleRate) {
        const double a = std::pow(10.0, gainDb / 40.0);
        const double w = 2.0 * M_PI * frequency / sampleRate;
        const double c = std::cos(w);
        const double beta = 2.0 * std::sqrt(a) * std::sin(w) / 2.0 * std::sqrt(2.0);
        return normalized(a * ((a + 1) + (a - 1) * c + beta), -2 * a * ((a - 1) + (a + 1) * c),
                          a * ((a + 1) + (a - 1) * c - beta), (a + 1) - (a - 1) * c + beta,
                          2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - beta);
    }

    static Biquad peak(double frequency, double q, double gainDb, int sampleRate) {
        const double a = std::pow(10.0, gainDb / 40.0);
        const double w = 2.0 * M_PI * frequency / sampleRate;
        const double alpha = std::sin(w) / (2.0 * q);
        const double c = std::cos(w);
        return normalized(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a);
    }

    float process(float input) {
        const double output = mB0 * input + mZ1;
        mZ1 = mB1 * input - mA1 * output + mZ2;
        mZ2 = mB2 * input - mA2 * output;
        return static_cast<float>(output);
    }

private:
    static Biquad normalized(double b0, double b1, double b2, double a0, double a1, double a2) {
        return {b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
    }

    double mB0, mB1, mB2, mA1, mA2;
    double mZ1 = 0.0;
    double mZ2 = 0.0;
};

/** Three-band tone control in dB: low shelf at 150 Hz, presence peak at 3 kHz, high shelf at 8 kHz. */
struct EqParameters {
    float lowDb;
    float presenceDb;
    float highDb;
};

inline void applyEq(std::vector<float> &samples, const EqParameters &eq, int sampleRate) {
    Biquad low = Biquad::lowShelf(150.0, eq.lowDb, sampleRate);
    Biquad presence = Biquad::peak(3000.0, 1.0, eq.presenceDb, sampleRate);
    Biquad high = Biquad::highShelf(8000.0, eq.highDb, sampleRate);
    for (float &sample : samples) sample = high.process(presence.process(low.process(sample)));
}

/** The three EQ bands of one channel as a running filter, for real-time processing. */
class EqFilter {
public:
    EqFilter(const EqParameters &eq, int sampleRate)
        : mLow(Biquad::lowShelf(150.0, eq.lowDb, sampleRate)),
          mPresence(Biquad::peak(3000.0, 1.0, eq.presenceDb, sampleRate)),
          mHigh(Biquad::highShelf(8000.0, eq.highDb, sampleRate)) {}

    float process(float sample) { return mHigh.process(mPresence.process(mLow.process(sample))); }

private:
    Biquad mLow;
    Biquad mPresence;
    Biquad mHigh;
};

/**
 * Feed-forward compressor with one control. [amount] 0..1 lowers the threshold from -10 to
 * -30 dBFS and raises the ratio from 1.5:1 to 5:1; 10 ms attack, 150 ms release, 6 dB soft knee.
 * Make-up gain gives back part of the reduction, so a stronger setting sounds denser, not quieter.
 */
class Compressor {
public:
    Compressor(float amount, int sampleRate) {
        const float strength = std::clamp(amount, 0.0f, 1.0f);
        mThreshold = -10.0f - 20.0f * strength;
        const float ratio = 1.5f + 3.5f * strength;
        mSlope = 1.0f - 1.0f / ratio;
        mMakeUpDb = 0.4f * -mThreshold * mSlope;
        mAttack = std::exp(-1.0f / (0.010f * sampleRate));
        mRelease = std::exp(-1.0f / (0.150f * sampleRate));
    }

    /** Gain for the next sample whose (linked) level is [level]; sample by sample, so it also runs in real time. */
    float gain(float level) {
        const float coefficient = level > mEnvelope ? mAttack : mRelease;
        mEnvelope = coefficient * mEnvelope + (1.0f - coefficient) * level;
        const float over = 20.0f * std::log10(mEnvelope + 1e-9f) - mThreshold;
        float reductionDb = 0.0f;
        if (over >= kKnee / 2) {
            reductionDb = over * mSlope;
        } else if (over > -kKnee / 2) {
            const float x = over + kKnee / 2;
            reductionDb = mSlope * x * x / (2.0f * kKnee);
        }
        return std::pow(10.0f, (mMakeUpDb - reductionDb) / 20.0f);
    }

private:
    static constexpr float kKnee = 6.0f;
    float mThreshold;
    float mSlope;
    float mMakeUpDb;
    float mAttack;
    float mRelease;
    float mEnvelope = 0.0f;
};

inline void applyCompressorChannels(std::vector<float> &samples, std::vector<float> *right,
                                    float amount, int sampleRate) {
    Compressor compressor(amount, sampleRate);
    for (size_t i = 0; i < samples.size(); ++i) {
        float &sample = samples[i];
        const float level = right ? std::max(std::fabs(sample), std::fabs((*right)[i])) : std::fabs(sample);
        const float gain = compressor.gain(level);
        sample *= gain;
        if (right) (*right)[i] *= gain;
    }
}

inline void applyCompressor(std::vector<float> &samples, float amount, int sampleRate) {
    applyCompressorChannels(samples, nullptr, amount, sampleRate);
}

/** One detector and gain for both channels, so compression never moves the stereo image. */
inline void applyStereoCompressor(std::vector<float> &left, std::vector<float> &right, float amount, int sampleRate) {
    applyCompressorChannels(left, &right, amount, sampleRate);
}

inline void normalizeStereoPeak(std::vector<float> &left, std::vector<float> &right, float targetDb) {
    float peak = 0.0f;
    for (float sample : left) peak = std::max(peak, std::fabs(sample));
    for (float sample : right) peak = std::max(peak, std::fabs(sample));
    if (peak < 1e-9f) return;
    const float gain = std::pow(10.0f, targetDb / 20.0f) / peak;
    for (float &sample : left) sample *= gain;
    for (float &sample : right) sample *= gain;
}

/** Stereo balance attenuates only the opposite channel; centre is transparent, no crossfeed. */
inline void balanceGains(float pan, float &left, float &right) {
    const float balance = std::clamp(pan, -1.0f, 1.0f);
    left = balance > 0 ? static_cast<float>(std::cos(balance * M_PI / 2)) : 1.0f;
    right = balance < 0 ? static_cast<float>(std::cos(balance * M_PI / 2)) : 1.0f;
}

/**
 * Constant-power panorama scaled so the centre keeps unity gain per channel: [pan] -1 = left,
 * 0 = centre, 1 = right. A mono track at the centre sounds exactly as before.
 */
inline void panGains(float pan, float &left, float &right) {
    const double angle = (std::clamp(pan, -1.0f, 1.0f) + 1.0) * M_PI / 4.0;
    left = static_cast<float>(std::cos(angle) * M_SQRT2);
    right = static_cast<float>(std::sin(angle) * M_SQRT2);
}

}  // namespace myaudio
