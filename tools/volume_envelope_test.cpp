#include "../app/src/main/cpp/VolumeEnvelope.h"
#include <cassert>
#include <cmath>
#include <cstdio>

int main() {
    using namespace myaudio;
    size_t segment = 0;
    assert(envelopeGain({}, 100, segment) == 1.0f);
    std::vector<VolumePoint> points{{480, 0.0f}, {960, 1.0f}, {1440, 0.5f}};
    assert(envelopeGain(points, 0, segment) == 0.0f);
    assert(envelopeGain(points, 480, segment) == 0.0f);
    assert(std::abs(envelopeGain(points, 720, segment) - 0.5f) < 1e-6f);
    assert(envelopeGain(points, 960, segment) == 1.0f);
    assert(envelopeGain(points, 1200, segment) == 0.75f);
    assert(envelopeGain(points, 1440, segment) == 0.5f);
    assert(envelopeGain(points, 48000, segment) == 0.5f); // reverb tail
    // The renderer supplies project coordinates, dropping latency and count-in from the file.
    points = {{0, 0.0f}, {48000, 1.0f}};
    segment = 0;
    const int64_t offset = -97020;
    assert(envelopeGain(points, offset + 97020, segment) == 0.0f);
    assert(envelopeGain(points, offset + 121020, segment) == 0.5f);
    points = {{100, 0.25f}};
    segment = 0;
    assert(envelopeGain(points, 0, segment) == 0.25f);
    assert(envelopeGain(points, 1000, segment) == 0.25f);
    std::puts("Volume envelope native checks passed");
}
