#include "TrackProcessing.h"

#include <algorithm>
#include <cmath>

#include "Metronome.h"

namespace myaudio {
namespace {

// Click removal filter: 512 taps (10.7 ms) starting 32 frames before the grid. Longer filters did
// not remove more on real Pixel 9 takes; the remaining click is non-linear speaker distortion.
constexpr int kTaps = 512;
constexpr int kPreTaps = 32;

/** Solves the symmetric positive definite system A x = b in place (A is n x n, row-major). */
bool choleskySolve(std::vector<double> &a, std::vector<double> &b, int n) {
    for (int j = 0; j < n; ++j) {
        double diagonal = a[j * n + j];
        for (int k = 0; k < j; ++k) diagonal -= a[j * n + k] * a[j * n + k];
        if (diagonal <= 0.0) return false;
        const double root = std::sqrt(diagonal);
        a[j * n + j] = root;
        for (int i = j + 1; i < n; ++i) {
            double value = a[i * n + j];
            for (int k = 0; k < j; ++k) value -= a[i * n + k] * a[j * n + k];
            a[i * n + j] = value / root;
        }
    }
    for (int i = 0; i < n; ++i) {
        double value = b[i];
        for (int k = 0; k < i; ++k) value -= a[i * n + k] * b[k];
        b[i] = value / a[i * n + i];
    }
    for (int i = n - 1; i >= 0; --i) {
        double value = b[i];
        for (int k = i + 1; k < n; ++k) value -= a[k * n + i] * b[k];
        b[i] = value / a[i * n + i];
    }
    return true;
}

std::vector<double> autocorrelation(const std::vector<float> &signal, int lags) {
    std::vector<double> result(lags, 0.0);
    for (int lag = 0; lag < lags; ++lag) {
        for (size_t n = 0; n + lag < signal.size(); ++n) result[lag] += static_cast<double>(signal[n]) * signal[n + lag];
    }
    return result;
}

}  // namespace

float removeMetronome(std::vector<float> &samples, int64_t offsetFrames, float bpm, float volume, int sampleRate) {
    if (samples.empty() || bpm <= 0.0f || volume <= 0.0f) return 0.0f;
    const ClickGrid grid(bpm, sampleRate);
    const auto length = static_cast<int64_t>(samples.size());
    const int clickLength = static_cast<int>(ClickGrid::click(false).size());
    const int span = clickLength + kTaps;  // samples influenced by one click

    // Beat-synchronous sums of the recording per click type: the least-squares problem only needs
    // the recording around the beats, and summing first keeps the cost independent of the length.
    std::vector<double> sums[2] = {std::vector<double>(span, 0.0), std::vector<double>(span, 0.0)};
    int64_t beats[2] = {0, 0};
    const int64_t firstBeat = std::max<int64_t>(0, grid.beatAt(offsetFrames));
    for (int64_t beat = firstBeat;; ++beat) {
        const int64_t start = grid.beatStart(beat) - offsetFrames - kPreTaps;  // track index of the window
        if (start >= length) break;
        if (start < 0 || start + span > length) continue;
        const int type = ClickGrid::isAccent(beat) ? 1 : 0;
        for (int j = 0; j < span; ++j) sums[type][j] += samples[start + j];
        ++beats[type];
    }
    if (beats[0] + beats[1] < 4) return 0.0f;

    // Normal equations: R(a, b) = autocorrelation of the reference at lag a - b (clicks never
    // overlap), p(a) = cross-correlation of recording and reference at tap a.
    std::vector<double> autocorr(kTaps, 0.0);
    std::vector<double> cross(kTaps, 0.0);
    for (int type = 0; type < 2; ++type) {
        if (beats[type] == 0) continue;
        const std::vector<float> &click = ClickGrid::click(type == 1);
        const std::vector<double> clickAutocorr = autocorrelation(click, kTaps);
        for (int lag = 0; lag < kTaps; ++lag) autocorr[lag] += beats[type] * volume * volume * clickAutocorr[lag];
        for (int tap = 0; tap < kTaps; ++tap) {
            double value = 0.0;
            for (int n = 0; n < clickLength; ++n) value += click[n] * sums[type][n + tap];
            cross[tap] += volume * value;
        }
    }
    std::vector<double> matrix(kTaps * kTaps);
    for (int a = 0; a < kTaps; ++a) {
        for (int b = 0; b < kTaps; ++b) matrix[a * kTaps + b] = autocorr[std::abs(a - b)];
        matrix[a * kTaps + a] += 1e-6 * autocorr[0];  // regularisation
    }
    std::vector<double> filter = cross;
    if (!choleskySolve(matrix, filter, kTaps)) return 0.0f;

    // Pre-compute the modelled click response per type and subtract it at every beat.
    std::vector<float> responses[2];
    for (int type = 0; type < 2; ++type) {
        const std::vector<float> &click = ClickGrid::click(type == 1);
        responses[type].assign(span, 0.0f);
        for (int n = 0; n < clickLength; ++n) {
            for (int tap = 0; tap < kTaps; ++tap) {
                responses[type][n + tap] += static_cast<float>(volume * click[n] * filter[tap]);
            }
        }
    }
    double before = 0.0, after = 0.0;
    for (int64_t beat = firstBeat;; ++beat) {
        const int64_t start = grid.beatStart(beat) - offsetFrames - kPreTaps;
        if (start >= length) break;
        const std::vector<float> &response = responses[ClickGrid::isAccent(beat) ? 1 : 0];
        for (int j = 0; j < span; ++j) {
            const int64_t index = start + j;
            if (index < 0 || index >= length) continue;
            before += static_cast<double>(samples[index]) * samples[index];
            samples[index] -= response[j];
            after += static_cast<double>(samples[index]) * samples[index];
        }
    }
    return after > 0.0 ? static_cast<float>(10.0 * std::log10(before / after)) : 0.0f;
}

bool removeMetronomeWithTemplate(std::vector<float> &samples, int64_t offsetFrames, int64_t countInFrames,
                                 float bpm, int sampleRate, float &removedDb) {
    if (samples.empty() || bpm <= 0.0f || countInFrames <= 0) return false;
    const ClickGrid grid(bpm, sampleRate);
    const auto length = static_cast<int64_t>(samples.size());
    const double framesPerBeat = 60.0 * sampleRate / bpm;
    // Template covers click plus room tail, but always ends before the next beat.
    const int span = static_cast<int>(std::min(0.3 * sampleRate, 0.9 * framesPerBeat));
    auto windowStart = [&](int64_t beat) { return grid.beatStart(beat) - offsetFrames - kPreTaps; };
    auto inside = [&](int64_t start) { return start >= 0 && start + span <= length; };

    // Average the count-in clicks per type (beats before project frame 0).
    std::vector<double> templates[2] = {std::vector<double>(span, 0.0), std::vector<double>(span, 0.0)};
    std::vector<int64_t> examples[2];
    for (int64_t beat = grid.beatAt(-countInFrames); beat < 0; ++beat) {
        if (grid.beatStart(beat) < -countInFrames) continue;
        const int64_t start = windowStart(beat);
        if (!inside(start)) continue;
        const int type = ClickGrid::isAccent(beat) ? 1 : 0;
        for (int j = 0; j < span; ++j) templates[type][j] += samples[start + j];
        examples[type].push_back(start);
    }
    for (int type = 0; type < 2; ++type) {
        if (examples[type].empty()) return false;
        for (double &value : templates[type]) value /= static_cast<double>(examples[type].size());
    }
    // Several examples of one type must agree; otherwise something else was audible (e.g. the
    // musician started early) and the template would paste that sound onto every beat.
    for (int type = 0; type < 2; ++type) {
        if (examples[type].size() < 2) continue;
        double templateEnergy = 0.0;
        for (double value : templates[type]) templateEnergy += value * value;
        for (int64_t start : examples[type]) {
            double deviation = 0.0;
            for (int j = 0; j < span; ++j) {
                const double difference = samples[start + j] - templates[type][j];
                deviation += difference * difference;
            }
            if (deviation > 0.25 * templateEnergy) return false;  // worse than -6 dB agreement
        }
    }

    // One global fit per type over all song beats: gain on the template and on its derivative
    // (a sub-sample shift). The music averages out over the beats; per-beat fits would not.
    double before = 0.0, after = 0.0;
    for (int type = 0; type < 2; ++type) {
        const std::vector<double> &shape = templates[type];
        std::vector<double> slope(span, 0.0);
        for (int j = 1; j + 1 < span; ++j) slope[j] = 0.5 * (shape[j + 1] - shape[j - 1]);
        double tt = 0.0, ts = 0.0, ss = 0.0;
        for (int j = 0; j < span; ++j) {
            tt += shape[j] * shape[j];
            ts += shape[j] * slope[j];
            ss += slope[j] * slope[j];
        }
        double xt = 0.0, xs = 0.0;
        int beats = 0;
        for (int64_t beat = 0;; ++beat) {
            const int64_t start = windowStart(beat);
            if (start >= length) break;
            if (ClickGrid::isAccent(beat) != (type == 1) || !inside(start)) continue;
            for (int j = 0; j < span; ++j) {
                xt += samples[start + j] * shape[j];
                xs += samples[start + j] * slope[j];
            }
            ++beats;
        }
        double gain = 1.0, shift = 0.0;
        const double determinant = tt * ss - ts * ts;
        if (beats > 0 && determinant > 0.0) {
            gain = (xt * ss - xs * ts) / (beats * determinant);
            shift = (xs * tt - xt * ts) / (beats * determinant);
        }
        // The count-in heard exactly the same click; a large deviation means the fit chased music.
        if (!(gain > 0.7 && gain < 1.3)) {
            gain = 1.0;
            shift = 0.0;
        }
        for (int64_t beat = 0;; ++beat) {
            const int64_t start = windowStart(beat);
            if (start >= length) break;
            if (ClickGrid::isAccent(beat) != (type == 1)) continue;
            for (int j = 0; j < span; ++j) {
                const int64_t index = start + j;
                if (index < 0 || index >= length) continue;
                before += static_cast<double>(samples[index]) * samples[index];
                samples[index] -= static_cast<float>(gain * shape[j] + shift * slope[j]);
                after += static_cast<double>(samples[index]) * samples[index];
            }
        }
    }
    removedDb = after > 0.0 ? static_cast<float>(10.0 * std::log10(before / after)) : 0.0f;
    return true;
}

void normalizePeak(std::vector<float> &samples, float targetDb) {
    float peak = 0.0f;
    for (float sample : samples) peak = std::max(peak, std::fabs(sample));
    if (peak < 1e-6f) return;
    const float gain = std::pow(10.0f, targetDb / 20.0f) / peak;
    for (float &sample : samples) sample *= gain;
}


namespace {

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
float allpass(DelayLine &line, float input, float gain) {
    const float delayed = line.read();
    const float v = input + gain * delayed;
    line.write(v);
    return delayed - gain * v;
}

int nextPrime(int value) {
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

}  // namespace

void applyReverb(std::vector<float> &left, std::vector<float> &right, const ReverbParameters &parameters,
                 int sampleRate, bool stereoInput) {
    if (!stereoInput) right = left;
    if (left.empty() || parameters.seconds <= 0.0f || parameters.mix <= 0.0f) return;
    const float tailSeconds = std::min(parameters.seconds * 1.1f + parameters.preDelayMs / 1000.0f, 12.0f);
    const auto tail = static_cast<size_t>(tailSeconds * sampleRate);

    // Normalise the wet path to unit impulse-response energy per channel, so "mix" means the
    // same wet/dry balance for a small chamber and a cathedral.
    Reverb probe(parameters, sampleRate);
    double energy = 0.0;
    for (size_t n = 0; n < tail; ++n) {
        float wetLeft = 0.0f;
        float wetRight = 0.0f;
        probe.process(n == 0 ? 1.0f : 0.0f, wetLeft, wetRight);
        energy += 0.5 * (static_cast<double>(wetLeft) * wetLeft + static_cast<double>(wetRight) * wetRight);
    }
    if (energy <= 0.0) return;
    // Equal-power crossfade: 0 = dry, 0.5 = equal energy, 1 = wet only.
    const float mix = std::clamp(parameters.mix, 0.0f, 1.0f);
    const auto dryGain = static_cast<float>(std::cos(mix * M_PI / 2));
    const auto wetGain = static_cast<float>(std::sin(mix * M_PI / 2) / std::sqrt(energy));

    Reverb reverb(parameters, sampleRate);
    // A second network preserves the right input, including anti-phase stereo material.
    Reverb rightReverb(parameters, sampleRate);
    const size_t dryLength = left.size();
    left.resize(dryLength + tail, 0.0f);
    right.resize(dryLength + tail, 0.0f);
    for (size_t n = 0; n < left.size(); ++n) {
        const float dry = n < dryLength ? left[n] : 0.0f;
        const float dryRight = n < dryLength ? right[n] : 0.0f;
        float wetLeft = 0.0f;
        float wetRight = 0.0f;
        reverb.process(dry, wetLeft, wetRight);
        if (stereoInput) {
            float unused = 0.0f;
            rightReverb.process(dryRight, unused, wetRight);
        }
        left[n] = dryGain * dry + wetGain * wetLeft;
        right[n] = dryGain * dryRight + wetGain * wetRight;
    }
}

}  // namespace myaudio
