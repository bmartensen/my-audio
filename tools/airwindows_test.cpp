// Every effect of the vendored Airwindows catalog runs through the effect chain with its default
// parameters: no crash, finite output, mono becomes stereo, tails ring out and get trimmed again,
// parameter names, defaults and display texts are complete. Also checks a few known effects.
#include <cassert>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include "../app/src/main/cpp/AirwindowsEffects.h"
#include "../app/src/main/cpp/ProjectMix.h"

namespace {

int indexOf(const char *name) {
    const auto &catalog = myaudio::airwindowsCatalog();
    for (size_t i = 0; i < catalog.size(); ++i) {
        if (std::string(catalog[i].name) == name) return static_cast<int>(i);
    }
    return -1;
}

std::vector<float> testSignal(int frames) {
    std::vector<float> signal(frames);
    for (int i = 0; i < frames; ++i) {
        const double t = i / 48000.0;
        signal[i] = static_cast<float>(0.4 * std::sin(2 * M_PI * 220 * t) * std::exp(-3.0 * std::fmod(t, 0.25)));
    }
    return signal;
}

}  // namespace

int main() {
    using namespace myaudio;
    const auto &catalog = airwindowsCatalog();
    assert(catalog.size() > 400);
    const std::vector<float> dry = testSignal(48000 / 2);
    const auto start = std::chrono::steady_clock::now();
    int silent = 0;
    for (size_t index = 0; index < catalog.size(); ++index) {
        const AirwindowsParameters parameters = airwindowsParameters(static_cast<int>(index));
        assert(parameters.names.size() == parameters.defaults.size());
        for (const auto &name : parameters.names) assert(!name.empty());
        const auto display = airwindowsDisplay(static_cast<int>(index), parameters.defaults);
        assert(display.size() == parameters.names.size());

        // Through the chain exactly as the app encodes it: type, count, index, values.
        std::vector<float> encoded = {3.0f, static_cast<float>(1 + parameters.defaults.size()), static_cast<float>(index)};
        encoded.insert(encoded.end(), parameters.defaults.begin(), parameters.defaults.end());
        const auto chain = decodeEffectChain(encoded.data(), encoded.size());
        assert(chain.size() == 1);
        std::vector<float> left = dry, right;
        bool stereo = false;
        applyEffectChain(chain, left, right, stereo, 48000);
        assert(stereo && left.size() == right.size() && left.size() >= dry.size());
        assert(left.size() <= dry.size() + 8 * 48000);
        double energy = 0;
        for (size_t i = 0; i < left.size(); ++i) {
            if (!std::isfinite(left[i]) || !std::isfinite(right[i])) {
                std::printf("FAIL %s: not finite at %zu\n", catalog[index].name, i);
                return 1;
            }
            energy += left[i] * left[i] + right[i] * right[i];
        }
        if (energy < 1e-9) ++silent;  // e.g. gates or effects whose default is "off" may stay quiet
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start);

    // Known effects: a tape saturator keeps the length, a hall rings out beyond the dry signal.
    const int tape = indexOf("ToTape9");
    const int hall = indexOf("Galactic");
    assert(tape >= 0 && hall >= 0);
    std::vector<float> left = dry, right = dry;
    assert(applyAirwindows(hall, airwindowsParameters(hall).defaults, left, right, 48000));
    assert(left.size() > dry.size());
    std::vector<float> values = airwindowsParameters(tape).defaults;
    assert(airwindowsParameters(tape).names.size() == 9);
    const auto texts = airwindowsDisplay(tape, values);
    assert(!texts[0].empty() && texts[0][0] != ' ');
    // An unknown index leaves the signal alone.
    left = dry; right = dry;
    assert(!applyAirwindows(100000, {}, left, right, 48000) && left == dry);

    std::printf("Airwindows: %zu effects rendered (%d quiet at defaults) in %lld ms\n", catalog.size(), silent,
                static_cast<long long>(elapsed.count()));
    return 0;
}
