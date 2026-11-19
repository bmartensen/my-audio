#include "BleedRemoval.h"

#include <algorithm>
#include <cmath>
#include <complex>

namespace myaudio {
namespace {

using Complex = std::complex<double>;

// The speaker-to-microphone path is short: 384 taps (8 ms incl. the pre-taps) removed the most
// real leak on Pixel 9 overdubs. Longer filters (up to 8192) fit the musical similarity between
// the takes and subtracted it as delayed copies, i.e. audible echoes. Non-linear (Hammerstein)
// branches removed nothing more, so the path model stays linear. Recording saturation and
// level-dependent playback gain are handled separately below.
constexpr int kTaps = 384;
constexpr int kPreTaps = 64;    // allow the path to start slightly before the calibrated latency
constexpr size_t kFftSize = 16384;
constexpr size_t kBlock = kFftSize - kTaps;  // linear correlation/convolution without wrap-around
// Estimate one short path from up to 60 s; its level can change with playback dynamics.
// A slow, coherence-gated scalar adjustment below follows that level without refitting taps.
constexpr int64_t kMaxEstimationFrames = 60 * 48000;
// WavWriter scales/clamps floats to +/-32767, read back at 1/32768.
constexpr float kPcmLimit = 32767.0f / 32768.0f;
constexpr int kClippingIterations = 4;
constexpr size_t kGainHop = 12000;  // 250 ms at the engine's fixed 48 kHz

/** In-place iterative radix-2 FFT; the inverse is scaled by 1/n. */
void fft(std::vector<Complex> &a, bool inverse) {
    const size_t n = a.size();
    for (size_t i = 1, j = 0; i < n; ++i) {
        size_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(a[i], a[j]);
    }
    for (size_t length = 2; length <= n; length <<= 1) {
        const double angle = 2.0 * M_PI / static_cast<double>(length) * (inverse ? 1.0 : -1.0);
        const Complex step(std::cos(angle), std::sin(angle));
        for (size_t start = 0; start < n; start += length) {
            Complex w(1.0, 0.0);
            for (size_t k = 0; k < length / 2; ++k) {
                const Complex u = a[start + k];
                const Complex v = a[start + k + length / 2] * w;
                a[start + k] = u + v;
                a[start + k + length / 2] = u - v;
                w *= step;
            }
        }
    }
    if (inverse) {
        for (Complex &value : a) value /= static_cast<double>(n);
    }
}

/** FFT of signal[start, start + count), zero-padded to kFftSize. */
std::vector<Complex> transform(const std::vector<float> &signal, int64_t start, size_t count) {
    std::vector<Complex> result(kFftSize);
    const auto length = static_cast<int64_t>(signal.size());
    for (size_t i = 0; i < count; ++i) {
        const int64_t index = start + static_cast<int64_t>(i);
        if (index >= 0 && index < length) result[i] = signal[index];
    }
    fft(result, false);
    return result;
}

/**
 * Solves the symmetric positive definite Toeplitz system T x = b (T given by its first column)
 * with the Levinson recursion (Golub & Van Loan, Alg. 4.7.2), O(n^2).
 */
std::vector<double> solveToeplitz(const std::vector<double> &column, const std::vector<double> &rhs) {
    const size_t n = column.size();
    std::vector<double> r(n - 1);
    for (size_t i = 0; i + 1 < n; ++i) r[i] = column[i + 1] / column[0];
    std::vector<double> x{rhs[0] / column[0]};
    std::vector<double> y{-r[0]};
    double beta = 1.0;
    double alpha = -r[0];
    std::vector<double> next;
    for (size_t k = 1; k < n; ++k) {
        beta *= 1.0 - alpha * alpha;
        double sum = 0.0;
        for (size_t i = 0; i < k; ++i) sum += r[i] * x[k - 1 - i];
        const double mu = (rhs[k] / column[0] - sum) / beta;
        next.assign(k + 1, 0.0);
        for (size_t i = 0; i < k; ++i) next[i] = x[i] + mu * y[k - 1 - i];
        next[k] = mu;
        x.swap(next);
        if (k + 1 < n) {
            sum = 0.0;
            for (size_t i = 0; i < k; ++i) sum += r[i] * y[k - 1 - i];
            alpha = (-r[k] - sum) / beta;
            next.assign(k + 1, 0.0);
            for (size_t i = 0; i < k; ++i) next[i] = y[i] + alpha * y[k - 1 - i];
            next[k] = alpha;
            y.swap(next);
        }
    }
    return x;
}

std::vector<float> convolve(const std::vector<float> &source, const std::vector<double> &filter) {
    std::vector<Complex> response(kFftSize);
    for (int i = 0; i < kTaps; ++i) response[i] = filter[i];
    fft(response, false);
    std::vector<float> model(source.size(), 0.0f);
    for (size_t start = 0; start < source.size(); start += kBlock) {
        std::vector<Complex> block = transform(source, static_cast<int64_t>(start), kBlock);
        for (size_t i = 0; i < kFftSize; ++i) block[i] *= response[i];
        fft(block, true);
        for (size_t i = 0; i < kFftSize && start + i < model.size(); ++i) {
            model[start + i] += static_cast<float>(block[i].real());
        }
    }
    return model;
}

std::vector<double> leakGains(const std::vector<float> &model, const std::vector<float> &samples,
                              const std::vector<float> &raw) {
    std::vector<double> gains;
    for (size_t center = 0; center < samples.size() + kGainHop; center += kGainHop) {
        const size_t start = center > kGainHop ? center - kGainHop : 0;
        const size_t end = std::min(samples.size(), center + kGainHop);
        double xx = 0.0, xy = 0.0, yy = 0.0;
        size_t count = 0;
        for (size_t j = start; j < end; ++j) {
            if (std::fabs(raw[j]) >= kPcmLimit) continue;
            const double x = model[j], y = samples[j];
            xx += x * x;
            xy += x * y;
            yy += y * y;
            ++count;
        }
        double gain = 1.0;
        if (count >= 960 && xx > 1e-8 && yy > 0.0 && xy > 0.0) {
            const double coherence = xy * xy / (xx * yy);
            const double candidate = xy / xx;
            // Require strong evidence of playback, so unrelated new music is not a gain cue.
            // Smoothly blend into adaptation; no binary gate on the musician's signal.
            const double trust = std::clamp((coherence - 0.8) / 0.15, 0.0, 1.0);
            if (candidate >= 0.5 && candidate <= 2.0) gain += trust * (candidate - 1.0);
        }
        gains.push_back(gain);
    }
    return gains;
}

}  // namespace

bool removeBleed(std::vector<float> &samples, const std::vector<float> &reference, int64_t latencyFrames,
                 float &removedDb, const std::vector<float> *rawSamples) {
    const auto length = static_cast<int64_t>(samples.size());
    if (length == 0 || reference.empty()) return false;
    if (rawSamples != nullptr && rawSamples->size() != samples.size()) return false;
    const auto &raw = rawSamples != nullptr ? *rawSamples : samples;
    std::vector<size_t> clipped;
    for (size_t j = 0; j < raw.size(); ++j) {
        if (std::fabs(raw[j]) >= kPcmLimit) clipped.push_back(j);
    }

    // Model: samples(j) ~ sum_l h(l) * source(j - l), with source(j) = reference(j + pre - latency),
    // i.e. the reference aligned to the take by the calibrated latency, starting kPreTaps early.
    std::vector<float> source(samples.size(), 0.0f);
    const int64_t shift = kPreTaps - latencyFrames;
    double referenceEnergy = 0.0;
    for (int64_t j = 0; j < length; ++j) {
        const int64_t index = j + shift;
        if (index >= 0 && index < static_cast<int64_t>(reference.size())) {
            source[j] = reference[index];
            referenceEnergy += static_cast<double>(source[j]) * source[j];
        }
    }
    if (referenceEnergy <= 0.0) return false;

    // Auto- and cross-correlation for lags 0..kTaps-1, accumulated block by block.
    std::vector<double> autocorr(kTaps, 0.0);
    std::vector<double> cross(kTaps, 0.0);
    std::vector<Complex> product(kFftSize);
    const int64_t estimationEnd = std::min(length, kMaxEstimationFrames);
    for (int64_t start = 0; start < estimationEnd; start += static_cast<int64_t>(kBlock)) {
        const std::vector<Complex> block = transform(source, start, kBlock);
        const std::vector<Complex> sourceSegment = transform(source, start, kFftSize);
        const std::vector<Complex> takeSegment = transform(samples, start, kFftSize);
        for (size_t i = 0; i < kFftSize; ++i) product[i] = std::conj(block[i]) * sourceSegment[i];
        fft(product, true);
        for (int lag = 0; lag < kTaps; ++lag) autocorr[lag] += product[lag].real();
        for (size_t i = 0; i < kFftSize; ++i) product[i] = std::conj(block[i]) * takeSegment[i];
        fft(product, true);
        for (int lag = 0; lag < kTaps; ++lag) cross[lag] += product[lag].real();
    }
    if (!(autocorr[0] > 0.0)) return false;  // reference may only start after the estimation window
    autocorr[0] *= 1.0 + 1e-4;  // regularisation against fitting music into silent passages
    std::vector<double> filter = solveToeplitz(autocorr, cross);
    std::vector<float> model = convolve(source, filter);

    // A saturated PCM sample is a bound, not the true microphone amplitude. Treating it as
    // exact biases the whole path estimate low. Refit using only model values beyond that
    // bound (censored observations); never invent missing peaks in the saved take itself.
    // The short filter remains constrained: a long FIR can subtract the musician's part.
    for (int iteration = 0; !clipped.empty() && iteration < kClippingIterations; ++iteration) {
        std::vector<float> restored = samples;
        for (size_t j : clipped) {
            restored[j] = raw[j] > 0.0f ? std::max(samples[j], model[j]) : std::min(samples[j], model[j]);
        }
        std::fill(cross.begin(), cross.end(), 0.0);
        for (int64_t start = 0; start < estimationEnd; start += static_cast<int64_t>(kBlock)) {
            const auto block = transform(source, start, kBlock);
            const auto takeSegment = transform(restored, start, kFftSize);
            for (size_t i = 0; i < kFftSize; ++i) product[i] = std::conj(block[i]) * takeSegment[i];
            fft(product, true);
            for (int lag = 0; lag < kTaps; ++lag) cross[lag] += product[lag].real();
        }
        filter = solveToeplitz(autocorr, cross);
        model = convolve(source, filter);
    }
    const auto gains = leakGains(model, samples, raw);
    double before = 0.0, after = 0.0;
    for (int64_t j = 0; j < length; ++j) {
        before += static_cast<double>(samples[j]) * samples[j];
        // Reproduce the recording's saturation before subtracting the leak; otherwise an
        // estimated +3 peak subtracted from a clipped +1 sample creates a new -2 impulse.
        // If click removal ran first, its subtraction shifts both saturation bounds.
        const float click = raw[j] - samples[j];
        const size_t segment = static_cast<size_t>(j) / kGainHop;
        const double fraction = static_cast<double>(j % kGainHop) / kGainHop;
        const float prediction = model[j] * static_cast<float>(gains[segment] + fraction *
                (gains[std::min(segment + 1, gains.size() - 1)] - gains[segment]));
        const float leak = clipped.empty() ? prediction : std::clamp(prediction, -kPcmLimit - click, kPcmLimit - click);
        samples[j] -= leak;
        after += static_cast<double>(samples[j]) * samples[j];
    }
    removedDb = after > 0.0 ? static_cast<float>(10.0 * std::log10(before / after)) : 0.0f;
    return true;
}

}  // namespace myaudio
