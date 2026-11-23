// Native test of imported tones (AmpCabinet.h): the zero-latency cabinet convolution equals a direct
// convolution, and both kinds give the same samples for any block size, so live monitoring sounds like the
// render. With a .nam path as argument, the amp model is checked the same way.
#include <cmath>
#include <cstdio>
#include <cstdint>
#include <algorithm>
#include <fstream>
#include <string>
#include <vector>

#include "../app/src/main/cpp/AmpCabinet.h"

using namespace myaudio;

namespace {

int failures = 0;

void check(bool ok, const char *what) {
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", what);
    if (!ok) ++failures;
}

/** Writes a mono 24-bit WAV at [rate]. */
void writeWav24(const std::string &path, const std::vector<float> &samples, int rate) {
    std::ofstream out(path, std::ios::binary);
    auto u32 = [&](uint32_t v) { out.put(v & 255); out.put(v >> 8 & 255); out.put(v >> 16 & 255); out.put(v >> 24 & 255); };
    auto u16 = [&](uint16_t v) { out.put(v & 255); out.put(v >> 8 & 255); };
    const uint32_t bytes = static_cast<uint32_t>(samples.size() * 3);
    out.write("RIFF", 4); u32(36 + bytes); out.write("WAVE", 4);
    out.write("fmt ", 4); u32(16); u16(1); u16(1); u32(rate); u32(rate * 3); u16(3); u16(24);
    out.write("data", 4); u32(bytes);
    for (float s : samples) {
        const auto v = static_cast<int32_t>(std::lrint(std::fmax(-1.0, std::fmin(1.0, s)) * 8388607.0));
        out.put(v & 255); out.put(v >> 8 & 255); out.put(v >> 16 & 255);
    }
}

std::vector<float> noise(size_t frames, uint32_t seed) {
    std::vector<float> samples(frames);
    for (float &s : samples) {
        seed = seed * 1664525u + 1013904223u;
        s = (static_cast<float>(seed >> 8) / static_cast<float>(1u << 24) * 2.0f - 1.0f) * 0.5f;
    }
    return samples;
}

/** Runs [kind]/[handle] over [input] in blocks of [block] frames. */
std::vector<float> run(ToneKind kind, int handle, const std::vector<float> &input, int block) {
    auto processor = ToneProcessor::create(kind, handle, 0.0f, 0.0f, false, 48000);
    std::vector<float> samples = input;
    for (size_t start = 0; start < samples.size(); start += block) {
        const int count = static_cast<int>(std::min<size_t>(block, samples.size() - start));
        processor->process(samples.data() + start, nullptr, count);
    }
    return samples;
}

double maxDifference(const std::vector<float> &a, const std::vector<float> &b) {
    double worst = 0.0;
    for (size_t i = 0; i < a.size() && i < b.size(); ++i) worst = std::fmax(worst, std::fabs(a[i] - b[i]));
    return worst;
}

}  // namespace

int main(int argc, char **argv) {
    // A decaying noise "cabinet" of 1000 frames at 48 kHz, longer than several 128-frame partitions.
    std::vector<float> ir = noise(1000, 7);
    for (size_t i = 0; i < ir.size(); ++i) ir[i] *= static_cast<float>(std::exp(-static_cast<double>(i) / 200.0));
#if defined(__ANDROID__)
    const std::string path = "/data/local/tmp/my-audio-amp-cabinet-test-ir.wav";
#else
    const std::string path = "amp-cabinet-test-ir.wav";
#endif
    writeWav24(path, ir, 48000);

    const ToneFileInfo info = inspectToneFile(path, ToneKind::Cabinet);
    check(info.ok && info.frames > 900, "IR file is accepted");
    const int handle = loadTone(path, ToneKind::Cabinet);
    check(handle >= 0, "IR loads");
    check(loadTone(path, ToneKind::Cabinet) == handle, "same file, same handle");

    // Reference: direct convolution with the IR as the processor stores it (24-bit, unit energy, trimmed).
    std::vector<float> stored;
    {
        std::vector<float> unit(ir.size(), 0.0f);
        unit[0] = 1.0f;
        stored = run(ToneKind::Cabinet, handle, unit, 4096);
    }
    const std::vector<float> input = noise(48000, 3);
    std::vector<float> direct(input.size(), 0.0f);
    for (size_t n = 0; n < input.size(); ++n) {
        double sum = 0.0;
        for (size_t k = 0; k < stored.size() && k <= n; ++k) sum += static_cast<double>(stored[k]) * input[n - k];
        direct[n] = static_cast<float>(sum);
    }
    const std::vector<float> whole = run(ToneKind::Cabinet, handle, input, static_cast<int>(input.size()));
    check(maxDifference(whole, direct) < 1e-4, "cabinet equals direct convolution without latency");
    check(maxDifference(run(ToneKind::Cabinet, handle, input, 96), whole) == 0.0, "cabinet: 96-frame blocks = render");
    check(maxDifference(run(ToneKind::Cabinet, handle, input, 37), whole) == 0.0, "cabinet: odd blocks = render");

    check(loadTone("missing.wav", ToneKind::Cabinet) < 0, "missing IR is rejected");
    check(!inspectToneFile(path, ToneKind::AmpModel).ok, "a WAV is no amp model");

    if (argc > 1) {
        const ToneFileInfo model = inspectToneFile(argv[1], ToneKind::AmpModel);
        check(model.ok, "amp model is accepted");
        const int amp = loadTone(argv[1], ToneKind::AmpModel);
        check(amp >= 0, "amp model loads");
        if (amp >= 0) {
            const std::vector<float> rendered = run(ToneKind::AmpModel, amp, input, static_cast<int>(input.size()));
            check(maxDifference(run(ToneKind::AmpModel, amp, input, 96), rendered) < 1e-6, "amp: 96-frame blocks = render");
            double energy = 0.0;
            for (float s : rendered) energy += s * s;
            check(energy > 0.0 && std::isfinite(energy), "amp model makes sound");
        }
    }
    std::remove(path.c_str());
    std::printf(failures == 0 ? "PASS\n" : "%d FAILED\n", failures);
    return failures == 0 ? 0 : 1;
}
