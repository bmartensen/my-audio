#pragma once

#include <vector>

namespace myaudio {

/** Strongest match of a template inside a recording window. */
struct CorrelationPeak {
    double lagFrames = 0.0;  // sub-sample position relative to the window start
    double snrDb = 0.0;      // peak power against the mean correlation power of the window
};

/** Exponential sine sweep from f0 to f1 Hz with short raised-cosine fades. */
std::vector<float> makeSweep(int sampleRate, double seconds, double f0, double f1, float amplitude);

/**
 * Cross-correlates [pattern] with [recording] for every lag 0..maxLag, starting at [start].
 * The caller guarantees start + maxLag + pattern.size() <= recording.size().
 */
CorrelationPeak findPeak(const std::vector<float> &recording, int start,
                         const std::vector<float> &pattern, int maxLag);

}  // namespace myaudio
