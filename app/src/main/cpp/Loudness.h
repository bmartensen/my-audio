#pragma once

#include <algorithm>
#include <cmath>
#include <deque>
#include <limits>
#include <vector>

#include "Tone.h"

namespace myaudio {

/**
 * Integrated loudness in LUFS after ITU-R BS.1770-4 for a 48 kHz stereo signal: K-weighting,
 * 400 ms blocks with 75 % overlap, absolute gate at -70 LUFS and relative gate 10 LU below.
 * Returns -infinity for silence.
 */
inline double integratedLoudness(const std::vector<float> &left, const std::vector<float> &right) {
    const size_t frames = std::min(left.size(), right.size());
    if (frames == 0) return -std::numeric_limits<double>::infinity();
    // K-weighting for 48 kHz, coefficients from the standard: high shelf, then high pass.
    auto kWeighted = [frames](const std::vector<float> &channel) {
        Biquad shelf(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585);
        Biquad highPass(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621);
        std::vector<double> squares(frames);
        for (size_t i = 0; i < frames; ++i) {
            const double value = highPass.process(shelf.process(channel[i]));
            squares[i] = value * value;
        }
        return squares;
    };
    const std::vector<double> l = kWeighted(left);
    const std::vector<double> r = kWeighted(right);
    constexpr size_t block = 19200;  // 400 ms
    constexpr size_t hop = 4800;     // 100 ms
    std::vector<double> powers;
    for (size_t start = 0; start == 0 || start + block <= frames; start += hop) {
        const size_t end = std::min(start + block, frames);
        double sum = 0.0;
        for (size_t i = start; i < end; ++i) sum += l[i] + r[i];
        powers.push_back(sum / static_cast<double>(end - start));
        if (end == frames) break;
    }
    auto loudness = [](double power) { return -0.691 + 10.0 * std::log10(power); };
    auto gatedMean = [&](double gateLufs) {
        double sum = 0.0;
        size_t count = 0;
        for (double power : powers) {
            if (power > 0.0 && loudness(power) > gateLufs) {
                sum += power;
                ++count;
            }
        }
        return count ? sum / static_cast<double>(count) : 0.0;
    };
    const double absolute = gatedMean(-70.0);
    if (absolute <= 0.0) return -std::numeric_limits<double>::infinity();
    const double relative = gatedMean(loudness(absolute) - 10.0);
    return relative > 0.0 ? loudness(relative) : -std::numeric_limits<double>::infinity();
}

/**
 * The same BS.1770-4 measurement as [integratedLoudness], streamed in 100 ms hops instead of
 * buffering every squared sample, so it stays small for long tracks (level matching runs it twice
 * per effect slot). Results agree with [integratedLoudness] up to summation order.
 */
inline double integratedLoudnessStreamed(const std::vector<float> &left, const std::vector<float> &right) {
    const size_t frames = std::min(left.size(), right.size());
    if (frames == 0) return -std::numeric_limits<double>::infinity();
    Biquad shelfL(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585);
    Biquad highPassL(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621);
    Biquad shelfR = shelfL;
    Biquad highPassR = highPassL;
    constexpr size_t block = 19200;  // 400 ms
    constexpr size_t hop = 4800;     // 100 ms
    std::vector<double> hops;
    hops.reserve(frames / hop + 1);
    double sum = 0.0;
    for (size_t i = 0; i < frames; ++i) {
        const double l = highPassL.process(shelfL.process(left[i]));
        const double r = highPassR.process(shelfR.process(right[i]));
        sum += l * l + r * r;
        if ((i + 1) % hop == 0 || i + 1 == frames) {
            hops.push_back(sum);
            sum = 0.0;
        }
    }
    std::vector<double> powers;
    if (frames < block) {
        double total = 0.0;
        for (double h : hops) total += h;
        powers.push_back(total / static_cast<double>(frames));
    } else {
        for (size_t k = 0; k * hop + block <= frames; ++k) {
            powers.push_back((hops[k] + hops[k + 1] + hops[k + 2] + hops[k + 3]) / static_cast<double>(block));
        }
    }
    auto loudness = [](double power) { return -0.691 + 10.0 * std::log10(power); };
    auto gatedMean = [&](double gateLufs) {
        double total = 0.0;
        size_t count = 0;
        for (double power : powers) {
            if (power > 0.0 && loudness(power) > gateLufs) {
                total += power;
                ++count;
            }
        }
        return count ? total / static_cast<double>(count) : 0.0;
    };
    const double absolute = gatedMean(-70.0);
    if (absolute <= 0.0) return -std::numeric_limits<double>::infinity();
    const double relative = gatedMean(loudness(absolute) - 10.0);
    return relative > 0.0 ? loudness(relative) : -std::numeric_limits<double>::infinity();
}

/**
 * Peak limiter for rendered audio: never exceeds [ceilingDb] (sample peak). The gain falls
 * smoothly over [lookaheadMs] before a peak and recovers with [releaseMs].
 */
inline void limitPeaks(std::vector<float> &left, std::vector<float> &right, float ceilingDb, int sampleRate,
                       float lookaheadMs = 5.0f, float releaseMs = 80.0f) {
    const size_t frames = std::min(left.size(), right.size());
    const float ceiling = std::pow(10.0f, ceilingDb / 20.0f);
    const auto window = static_cast<size_t>(std::max(1.0f, lookaheadMs * sampleRate / 1000.0f));
    std::vector<float> needed(frames);
    for (size_t i = 0; i < frames; ++i) {
        const float peak = std::max(std::fabs(left[i]), std::fabs(right[i]));
        needed[i] = peak > ceiling ? ceiling / peak : 1.0f;
    }
    // Forward minimum over the look-ahead window, then a moving average of the same length: the
    // gain ramps down before each peak and is at most the needed gain at the peak itself.
    std::vector<float> minimum(frames);
    std::deque<size_t> candidates;
    for (size_t i = frames; i-- > 0;) {
        while (!candidates.empty() && needed[candidates.back()] >= needed[i]) candidates.pop_back();
        candidates.push_back(i);
        while (candidates.front() >= i + window) candidates.pop_front();
        minimum[i] = needed[candidates.front()];
    }
    const float release = std::exp(-1.0f / (releaseMs * sampleRate / 1000.0f));
    // Unity gain before the first frame fills the averaging window.
    double sum = static_cast<double>(window);
    float gain = 1.0f;
    for (size_t i = 0; i < frames; ++i) {
        sum += minimum[i] - (i >= window ? minimum[i - window] : 1.0f);
        const auto target = static_cast<float>(sum / static_cast<double>(window));
        gain = target < gain ? target : release * gain + (1.0f - release) * target;
        left[i] = std::clamp(left[i] * gain, -ceiling, ceiling);
        right[i] = std::clamp(right[i] * gain, -ceiling, ceiling);
    }
}

/**
 * Master stage of the mix: brings the integrated loudness to [targetLufs] (boost at most
 * [maxGainDb], so near-silent projects do not lift their noise) and limits peaks to [ceilingDb].
 */
inline void masterLoudness(std::vector<float> &left, std::vector<float> &right, float targetLufs, float ceilingDb,
                           float maxGainDb, int sampleRate) {
    const double loudness = integratedLoudness(left, right);
    if (!std::isfinite(loudness)) return;
    const auto gain = static_cast<float>(std::pow(10.0, std::min<double>(targetLufs - loudness, maxGainDb) / 20.0));
    for (float &sample : left) sample *= gain;
    for (float &sample : right) sample *= gain;
    limitPeaks(left, right, ceilingDb, sampleRate);
}

}  // namespace myaudio
