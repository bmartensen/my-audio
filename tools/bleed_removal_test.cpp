#include <cassert>
#include <cmath>
#include <cstdio>
#include <random>
#include <vector>

#include "../app/src/main/cpp/BleedRemoval.cpp"

namespace {
constexpr float kLimit = 32767.0f / 32768.0f;

double error(const std::vector<float> &actual, const std::vector<float> &expected) {
    double sum = 0.0;
    for (size_t i = 0; i < actual.size(); ++i) {
        const double delta = actual[i] - expected[i];
        sum += delta * delta;
    }
    return std::sqrt(sum / actual.size());
}

void checkRecovery(float level, bool withClick, bool quietStart = false) {
    constexpr size_t n = 240000;
    constexpr int latency = 180;
    std::mt19937 rng(31415);
    std::normal_distribution<float> noise(0.0f, 1.0f);
    std::vector<float> ref(n), near(n), raw(n), clean(n);
    float memory = 0.0f, nearMemory = 0.0f;
    for (size_t i = 0; i < n; ++i) {
        memory = noise(rng) * 0.14f + memory * 0.6f;
        nearMemory = noise(rng) * 0.02f + nearMemory * 0.7f;
        ref[i] = memory * level * (quietStart && i < 72000 ? 0.03f : 1.0f);
        near[i] = nearMemory * (quietStart && i < 72000 ? 0.01f : 1.0f);
    }
    size_t clipped = 0;
    for (size_t i = 0; i < n; ++i) {
        const float echoGain = quietStart && i < 72000 ? 1.6f : 1.0f;
        const float echo = echoGain * ((i >= latency ? 1.8f * ref[i - latency] : 0.0f) +
                           (i >= latency + 15 ? 0.2f * ref[i - latency - 15] : 0.0f));
        const float click = withClick && i % 24000 < 200 ? 0.4f * std::sin(i * 0.7f) : 0.0f;
        raw[i] = std::clamp(echo + near[i] + click, -kLimit, kLimit);
        if (std::fabs(raw[i]) >= kLimit) ++clipped;
        clean[i] = raw[i] - click;
    }
    const double before = error(clean, near);
    float removedDb = 0.0f;
    assert(myaudio::removeBleed(clean, ref, latency, removedDb, withClick ? &raw : nullptr));
    const double after = error(clean, near);
    std::printf("level %.1f click %d clipped %zu: error %.6f -> %.6f\n", level, withClick, clipped, before, after);
    assert(std::isfinite(removedDb));
    // Known independent new performance must survive, not merely become quieter.
    assert(after < (level < 1.0f ? 0.003 : 0.014));
    assert(after < before * 0.08);
    if (level < 1.0f) assert(clipped == 0);
    else assert(clipped > 0);
    if (quietStart) {
        const std::vector<float> pause(clean.begin() + 12000, clean.begin() + 60000);
        const std::vector<float> target(near.begin() + 12000, near.begin() + 60000);
        const double pauseError = error(pause, target);
        std::printf("quiet passage error %.6f\n", pauseError);
        assert(pauseError < 0.001);
    }
}
}  // namespace

int main() {
    for (float level : {0.3f, 1.0f, 2.0f}) {
        checkRecovery(level, false);
        checkRecovery(level, true);
    }
    checkRecovery(1.0f, false, true);
    float removedDb = 0.0f;
    std::vector<float> samples(1000, 0.1f), reference(1000, 0.0f);
    const auto unchanged = samples;
    assert(!myaudio::removeBleed(samples, reference, 0, removedDb));
    assert(samples == unchanged);
    reference.assign(1000, 0.1f);
    assert(!myaudio::removeBleed(samples, reference, 2000, removedDb));
    assert(samples == unchanged);
    std::vector<float> wrongSize(2);
    assert(!myaudio::removeBleed(samples, reference, 0, removedDb, &wrongSize));
    assert(samples == unchanged);
    // A late reference outside the estimation window must not produce NaNs.
    samples.assign(60 * 48000 + 20000, 0.0f);
    reference = samples;
    reference.back() = 0.5f;
    assert(!myaudio::removeBleed(samples, reference, 0, removedDb));
    std::puts("bleed_removal_test OK");
}
