#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

#include "../app/src/main/cpp/AirwindowsEffects.h"
#include "../app/src/main/cpp/Loudness.h"
#include "../app/src/main/cpp/ProjectMix.h"
#include "../app/src/main/cpp/Tone.h"
#include "../app/src/main/cpp/TrackProcessing.h"
#include "../app/src/main/cpp/Wav.h"

int main() {
    using namespace myaudio;
#if defined(__ANDROID__)
    const std::string prefix = "/data/local/tmp/my-audio-stereo-test";
#else
    const std::string prefix = "stereo-test";
#endif
    const std::string input = prefix + ".wav", stem = prefix + "-stem.wav";
    constexpr int frames = 48000;
    std::vector<int16_t> pcm(frames * 2);
    for (int i = 0; i < frames; ++i) {
        pcm[i * 2] = static_cast<int16_t>(10000 * std::sin(2 * M_PI * 440 * i / 48000));
        pcm[i * 2 + 1] = -pcm[i * 2];
    }
    WavWriter writer;
    assert(writer.open(input, 48000, 2));
    assert(writer.write(pcm.data(), frames));
    assert(writer.finish() == frames);
    int rate = 0, channels = 0;
    std::vector<int16_t> read;
    assert(readWav16(input, read, rate, channels) && channels == 2 && rate == 48000 && read == pcm);
    assert(!readWavMono16(input, read, rate));

    TrackSource source{};
    source.path = input;
    source.gain = 1.0f;
    source.normalizeDb = std::numeric_limits<float>::quiet_NaN();
    std::vector<float> left, right;
    renderProjectMix({source}, {}, left, right);
    assert(left.size() == frames && right.size() == frames);
    double power = 0;
    for (int i = 0; i < frames; ++i) {
        assert(std::isfinite(left[i]) && std::fabs(left[i] + right[i]) < 1e-6f);
        power += left[i] * left[i];
    }
    assert(power / frames > 0.001); // A mono fold-down would be entirely silent.
    const auto centred = left;
    source.pan = -1;
    renderProjectMix({source}, {}, left, right);
    for (float v : right) assert(std::fabs(v) < 1e-6f);
    source.pan = 1;
    renderProjectMix({source}, {}, left, right);
    for (float v : left) assert(std::fabs(v) < 1e-6f);
    source.pan = 0;
    source.volumeEnvelope = {{0, 0}, {frames / 2, 0}, {frames - 1, 1}};
    renderProjectMix({source}, {}, left, right);
    for (int i = 0; i < frames / 2; ++i) assert(left[i] == 0 && right[i] == 0);
    source.volumeEnvelope.clear();

    assert(writeStems({source}, frames + 100, {stem}));
    assert(readWav16(stem, read, rate, channels) && channels == 2 && read.size() == (frames + 100) * 2);
    for (int i = 0; i < frames * 2; ++i) assert(std::abs(static_cast<int>(read[i]) - pcm[i]) <= 1);
    for (size_t i = frames * 2; i < read.size(); ++i) assert(read[i] == 0);

    left = centred; right = centred;
    for (float &v : right) v *= -0.25f;
    normalizeStereoPeak(left, right, -6);
    applyStereoCompressor(left, right, 0.7f, 48000);
    for (int i = 0; i < frames; ++i) assert(std::fabs(right[i] + left[i] * 0.25f) < 1e-6f);

    // Stereo reverb keeps the dry signal separate, including an isolated right impulse.
    left.assign(4800, 0); right.assign(4800, 0); right[10] = 0.5f;
    applyReverb(left, right, {0.2f, 20, 0.4f, 0.25f}, 48000, true);
    assert(std::fabs(right[10] - 0.5f * std::cos(0.25f * M_PI / 2)) < 1e-5f);
    for (float v : left) assert(v == 0);
    double tailPower = 0;
    for (size_t i = 4800; i < right.size(); ++i) tailPower += right[i] * right[i];
    assert(tailPower > 0);

    // A level-matched Airwindows Drive keeps the loudness of its input; unmatched it is much louder.
    {
        int drive = -1;
        const auto &catalog = airwindowsCatalog();
        for (size_t i = 0; i < catalog.size(); ++i) if (std::strcmp(catalog[i].name, "Drive") == 0) drive = static_cast<int>(i);
        assert(drive >= 0);
        std::vector<float> tone(48000 * 3);
        for (size_t i = 0; i < tone.size(); ++i) tone[i] = 0.2f * static_cast<float>(std::sin(2 * M_PI * 220 * i / 48000));
        const double input = integratedLoudness(tone, tone);
        assert(std::fabs(integratedLoudnessStreamed(tone, tone) - input) < 1e-6);
        auto loudnessAfter = [&](EffectType type) {
            std::vector<float> l = tone, r;
            bool stereo = false;
            applyEffectChain({{type, {static_cast<float>(drive), 0.85f, 0.648f, 1.0f, 1.0f}}}, l, r, stereo, 48000);
            assert(stereo && l.size() == r.size());
            return integratedLoudness(l, r);
        };
        assert(loudnessAfter(EffectType::Airwindows) > input + 3);
        assert(std::fabs(loudnessAfter(EffectType::AirwindowsLevelMatched) - input) < 0.2);
    }

    // Existing mono takes still duplicate to stereo and export as mono.
    assert(writer.open(input, 48000));
    assert(writer.write(pcm.data(), frames));
    assert(writer.finish() == frames);
    renderProjectMix({source}, {}, left, right);
    assert(left == right && left.size() == frames);
    assert(writeStems({source}, frames, {stem}));
    assert(readWav16(stem, read, rate, channels) && channels == 1 && read.size() == frames);
    std::remove(input.c_str()); std::remove(stem.c_str());
    std::puts("Stereo mix: phase, balance, envelope, linked effects, reverb, level match, WAV/stems and legacy mono passed");
}
