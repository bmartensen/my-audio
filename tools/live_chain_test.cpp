#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "../app/src/main/cpp/AirwindowsEffects.h"
#include "../app/src/main/cpp/LiveChain.h"
#include "../app/src/main/cpp/ProjectMix.h"
#include "../app/src/main/cpp/Tone.h"

// Live monitoring: the real-time chain sounds like the rendered mix, and monitoring never touches its input.
int main() {
    using namespace myaudio;
    constexpr int kRate = 48000;
    constexpr int kFrames = kRate * 2;
    constexpr int kBlock = 192;  // a typical Android burst

    // A plucked-string-like test signal: decaying harmonics plus a little noise.
    std::vector<float> input(kFrames);
    uint32_t seed = 1;
    for (int i = 0; i < kFrames; ++i) {
        const double t = static_cast<double>(i % (kRate / 2)) / kRate;
        double value = 0.0;
        for (int harmonic = 1; harmonic <= 5; ++harmonic) value += std::sin(2 * M_PI * 196.0 * harmonic * t) / harmonic;
        seed = seed * 1664525u + 1013904223u;
        value = 0.25 * value * std::exp(-4.0 * t) + 0.002 * (static_cast<double>(seed >> 8) / (1u << 24) - 0.5);
        input[i] = static_cast<float>(value);
    }

    int galactic = -1;
    for (size_t i = 0; i < airwindowsCatalog().size(); ++i) {
        if (std::strcmp(airwindowsCatalog()[i].name, "Galactic") == 0) galactic = static_cast<int>(i);
    }
    assert(galactic >= 0);
    const std::vector<float> galacticValues = airwindowsParameters(galactic).defaults;
    std::vector<float> airwindows = {static_cast<float>(galactic)};
    airwindows.insert(airwindows.end(), galacticValues.begin(), galacticValues.end());
    const std::vector<EffectSlot> chain = {
            {EffectType::Tone, {-3.0f, 3.0f, 2.0f, 0.5f}},
            {EffectType::Reverb, {1.8f, 20.0f, 0.4f, 0.25f}},
            {EffectType::Airwindows, airwindows},
    };

    // Rendered like a mono take in the mix: centre pan is unity gain per channel.
    std::vector<float> left = input;
    std::vector<float> right;
    bool stereo = false;
    applyEffectChain(chain, left, right, stereo, kRate);
    const std::vector<float> &offlineRight = stereo ? right : left;

    LiveChain live(chain, 1.0f, 0.0f, false, kRate);
    std::vector<float> liveLeft(kFrames);
    std::vector<float> liveRight(kFrames);
    for (int start = 0; start < kFrames; start += kBlock) {
        const int count = std::min(kBlock, kFrames - start);
        live.process(input.data() + start, liveLeft.data() + start, liveRight.data() + start, count);
    }
    float maxDifference = 0.0f;
    for (int i = 0; i < kFrames; ++i) {
        maxDifference = std::max(maxDifference, std::fabs(liveLeft[i] - left[i]));
        maxDifference = std::max(maxDifference, std::fabs(liveRight[i] - offlineRight[i]));
    }
    std::printf("live vs rendered chain: max difference %.3g\n", maxDifference);
    assert(maxDifference < 1e-4f);

    // Monitoring reads the microphone and only adds to the output; the take is written from the untouched input.
    const std::vector<float> untouched = input;
    LiveMonitor monitor;
    monitor.setChain(std::make_unique<LiveChain>(chain, 1.0f, 0.0f, false, kRate));
    std::vector<float> output(static_cast<size_t>(kFrames) * 2, 0.0f);
    for (int start = 0; start < kFrames; start += kBlock) {
        const int count = std::min(kBlock, kFrames - start);
        monitor.process(input.data() + start, output.data() + static_cast<size_t>(start) * 2, 2, count);
    }
    assert(input == untouched);
    // The first chain fades in from silence over 20 ms; afterwards the output is the chain itself.
    float monitorDifference = 0.0f;
    for (int i = 960; i < kFrames; ++i) {
        monitorDifference = std::max(monitorDifference, std::fabs(output[i * 2] - std::clamp(liveLeft[i], -1.0f, 1.0f)));
        monitorDifference = std::max(monitorDifference, std::fabs(output[i * 2 + 1] - std::clamp(liveRight[i], -1.0f, 1.0f)));
    }
    std::printf("monitor vs chain after the fade: max difference %.3g\n", monitorDifference);
    assert(monitorDifference < 1e-4f);

    // Switching to a dry chain crossfades without a jump; a silent chain stops monitoring.
    monitor.setChain(std::make_unique<LiveChain>(std::vector<EffectSlot>{}, 1.0f, 0.0f, false, kRate));
    std::vector<float> dry(static_cast<size_t>(kBlock) * 2 * 10, 0.0f);
    for (int block = 0; block < 10; ++block) {
        monitor.process(input.data() + block * kBlock, dry.data() + static_cast<size_t>(block) * kBlock * 2, 2, kBlock);
    }
    // Centre pan is unity gain up to float rounding.
    for (int i = 960; i < kBlock * 10; ++i) {
        assert(std::fabs(dry[i * 2] - input[i]) < 1e-6f && std::fabs(dry[i * 2 + 1] - input[i]) < 1e-6f);
    }
    monitor.setChain(std::make_unique<LiveChain>(std::vector<EffectSlot>{}, 1.0f, 0.0f, true, kRate));
    std::vector<float> silent(static_cast<size_t>(kBlock) * 2 * 10, 0.0f);
    for (int block = 0; block < 10; ++block) {
        monitor.process(input.data() + block * kBlock, silent.data() + static_cast<size_t>(block) * kBlock * 2, 2, kBlock);
    }
    for (int i = 960; i < kBlock * 10; ++i) assert(silent[i * 2] == 0.0f && silent[i * 2 + 1] == 0.0f);

    // Level matching live uses an estimate; it must build and give a finite, audible signal.
    const std::vector<EffectSlot> matched = {{EffectType::AirwindowsLevelMatched, airwindows}};
    LiveChain matchedChain(matched, 1.0f, 0.0f, false, kRate);
    std::vector<float> matchedLeft(kBlock);
    std::vector<float> matchedRight(kBlock);
    float energy = 0.0f;
    for (int start = 0; start < kRate / 2; start += kBlock) {
        matchedChain.process(input.data() + start, matchedLeft.data(), matchedRight.data(), kBlock);
        for (int i = 0; i < kBlock; ++i) {
            assert(std::isfinite(matchedLeft[i]) && std::isfinite(matchedRight[i]));
            energy += matchedLeft[i] * matchedLeft[i];
        }
    }
    assert(energy > 0.0f);
    std::printf("live chain test passed\n");
    return 0;
}
