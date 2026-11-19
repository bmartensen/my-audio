#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

#include "../app/src/main/cpp/Loudness.h"

namespace {

constexpr int kRate = 48000;

std::vector<float> sine(double frequency, double amplitude, double seconds) {
    std::vector<float> samples(static_cast<size_t>(seconds * kRate));
    for (size_t i = 0; i < samples.size(); ++i) {
        samples[i] = static_cast<float>(amplitude * std::sin(2.0 * M_PI * frequency * i / kRate));
    }
    return samples;
}

double rmsDb(const std::vector<float> &samples, size_t from) {
    double sum = 0.0;
    for (size_t i = from; i < samples.size(); ++i) sum += samples[i] * samples[i];
    return 10.0 * std::log10(sum / static_cast<double>(samples.size() - from));
}

double peak(const std::vector<float> &samples) {
    double result = 0.0;
    for (float sample : samples) result = std::max(result, static_cast<double>(std::fabs(sample)));
    return result;
}

}  // namespace

int main() {
    using namespace myaudio;

    // A flat EQ leaves the signal (practically) untouched.
    std::vector<float> flat = sine(1000.0, 0.5, 1.0);
    const double before = rmsDb(flat, 4800);
    applyEq(flat, {0.0f, 0.0f, 0.0f}, kRate);
    assert(std::fabs(rmsDb(flat, 4800) - before) < 0.01);

    // Shelves act where they should: +6 dB lows lift 50 Hz, leave 1 kHz nearly alone.
    std::vector<float> bass = sine(50.0, 0.25, 1.0);
    std::vector<float> mid = sine(1000.0, 0.25, 1.0);
    const double bassBefore = rmsDb(bass, 9600);
    const double midBefore = rmsDb(mid, 9600);
    applyEq(bass, {6.0f, 0.0f, 0.0f}, kRate);
    applyEq(mid, {6.0f, 0.0f, 0.0f}, kRate);
    assert(std::fabs(rmsDb(bass, 9600) - bassBefore - 6.0) < 0.5);
    assert(std::fabs(rmsDb(mid, 9600) - midBefore) < 0.6);

    // The compressor narrows the distance between a loud and a quiet passage.
    std::vector<float> loud = sine(440.0, 0.9, 1.0);
    std::vector<float> quiet = sine(440.0, 0.05, 1.0);
    const double distance = rmsDb(loud, 9600) - rmsDb(quiet, 9600);
    applyCompressor(loud, 0.7f, kRate);
    applyCompressor(quiet, 0.7f, kRate);
    assert(rmsDb(loud, 9600) - rmsDb(quiet, 9600) < distance - 6.0);

    // Panorama: unity in both channels at the centre, constant power, hard left only left.
    float left = 0.0f, right = 0.0f;
    panGains(0.0f, left, right);
    assert(std::fabs(left - 1.0f) < 1e-5f && std::fabs(right - 1.0f) < 1e-5f);
    panGains(-1.0f, left, right);
    assert(std::fabs(left * left + right * right - 2.0f) < 1e-4f && right < 1e-5f);

    // BS.1770 reference: a full-scale 997 Hz sine in one channel measures -3.01 LUFS.
    std::vector<float> reference = sine(997.0, 1.0, 5.0);
    std::vector<float> silent(reference.size(), 0.0f);
    assert(std::fabs(integratedLoudness(reference, silent) + 3.01) < 0.05);
    assert(std::isinf(integratedLoudness(silent, silent)));

    // The master stage reaches -14 LUFS for a quiet mix and never exceeds -1 dBFS.
    std::vector<float> l = sine(220.0, 0.05, 5.0);
    std::vector<float> r = sine(330.0, 0.05, 5.0);
    masterLoudness(l, r, -14.0f, -1.0f, 24.0f, kRate);
    assert(std::fabs(integratedLoudness(l, r) + 14.0) < 0.3);
    const double ceiling = std::pow(10.0, -1.0 / 20.0);
    assert(peak(l) <= ceiling + 1e-6 && peak(r) <= ceiling + 1e-6);

    // A hot mix with a single spike: the limiter catches the spike, the rest stays put.
    std::vector<float> hotLeft = sine(220.0, 0.3, 2.0);
    std::vector<float> hotRight = hotLeft;
    hotLeft[48000] = 1.0f;
    limitPeaks(hotLeft, hotRight, -1.0f, kRate);
    assert(peak(hotLeft) <= ceiling + 1e-6);
    assert(std::fabs(hotLeft[24000] - static_cast<float>(0.3 * std::sin(2.0 * M_PI * 220.0 * 24000 / kRate))) < 1e-4f);

    // Near silence is lifted at most by the cap.
    std::vector<float> faintLeft = sine(440.0, 0.0005, 2.0);
    std::vector<float> faintRight = faintLeft;
    const double faintBefore = integratedLoudness(faintLeft, faintRight);
    masterLoudness(faintLeft, faintRight, -14.0f, -1.0f, 24.0f, kRate);
    assert(integratedLoudness(faintLeft, faintRight) - faintBefore < 24.1);

    std::puts("Master and tone: EQ, compressor, panorama, BS.1770 reference, -14 LUFS, limiter and boost cap passed");
    return 0;
}
