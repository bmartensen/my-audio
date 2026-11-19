#include "Correlation.h"

#include <algorithm>
#include <cmath>

namespace myaudio {

std::vector<float> makeSweep(int sampleRate, double seconds, double f0, double f1, float amplitude) {
    const int length = static_cast<int>(seconds * sampleRate);
    const int fade = sampleRate / 500;  // 2 ms
    const double ratio = std::log(f1 / f0);
    std::vector<float> sweep(length);
    for (int n = 0; n < length; ++n) {
        const double t = static_cast<double>(n) / sampleRate;
        const double phase = 2.0 * M_PI * f0 * seconds / ratio * (std::exp(t / seconds * ratio) - 1.0);
        double gain = 1.0;
        if (n < fade) gain = 0.5 - 0.5 * std::cos(M_PI * n / fade);
        else if (n >= length - fade) gain = 0.5 - 0.5 * std::cos(M_PI * (length - 1 - n) / fade);
        sweep[n] = static_cast<float>(amplitude * gain * std::sin(phase));
    }
    return sweep;
}

CorrelationPeak findPeak(const std::vector<float> &recording, int start,
                         const std::vector<float> &pattern, int maxLag) {
    const int length = static_cast<int>(pattern.size());
    std::vector<float> magnitude(maxLag + 1);
    for (int lag = 0; lag <= maxLag; ++lag) {
        const float *window = recording.data() + start + lag;
        float sum = 0.0f;
        for (int n = 0; n < length; ++n) sum += pattern[n] * window[n];
        magnitude[lag] = std::fabs(sum);
    }

    const int best = static_cast<int>(std::max_element(magnitude.begin(), magnitude.end()) - magnitude.begin());

    // Noise floor: mean power away from the main lobe (±1 ms at 48 kHz).
    double noise = 0.0;
    int noiseCount = 0;
    for (int lag = 0; lag <= maxLag; ++lag) {
        if (std::abs(lag - best) > 48) {
            noise += static_cast<double>(magnitude[lag]) * magnitude[lag];
            ++noiseCount;
        }
    }
    const double peakPower = static_cast<double>(magnitude[best]) * magnitude[best];
    const double noisePower = noiseCount > 0 ? noise / noiseCount : 0.0;

    // Parabolic interpolation around the maximum for sub-sample resolution.
    double offset = 0.0;
    if (best > 0 && best < maxLag) {
        const double y0 = magnitude[best - 1], y1 = magnitude[best], y2 = magnitude[best + 1];
        const double denominator = y0 - 2.0 * y1 + y2;
        if (denominator != 0.0) offset = 0.5 * (y0 - y2) / denominator;
    }

    CorrelationPeak peak;
    peak.lagFrames = best + offset;
    peak.snrDb = noisePower > 0.0 ? 10.0 * std::log10(peakPower / noisePower) : 0.0;
    return peak;
}

}  // namespace myaudio
