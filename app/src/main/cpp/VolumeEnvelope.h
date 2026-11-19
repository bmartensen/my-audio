#pragma once

#include <algorithm>
#include <cstdint>
#include <vector>

namespace myaudio {
struct VolumePoint {
    int64_t frame;
    float gain;
};

// Points are sorted and unique, with project-frame coordinates. Hold the endpoint gains
// outside the envelope, interpolate linearly inside it. No points preserves older projects.
inline float envelopeGain(const std::vector<VolumePoint> &points, int64_t frame, size_t &segment) {
    if (points.empty()) return 1.0f;
    while (segment + 1 < points.size() && frame >= points[segment + 1].frame) ++segment;
    if (frame <= points.front().frame) return points.front().gain;
    if (segment + 1 == points.size()) return points.back().gain;
    const auto &a = points[segment];
    const auto &b = points[segment + 1];
    const double t = static_cast<double>(frame - a.frame) / static_cast<double>(b.frame - a.frame);
    return a.gain + static_cast<float>(t) * (b.gain - a.gain);
}
}
