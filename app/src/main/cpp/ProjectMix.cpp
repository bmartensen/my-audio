#include "ProjectMix.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>

#include "AirwindowsEffects.h"
#include "AmpCabinet.h"
#include "BleedRemoval.h"
#include "Log.h"
#include "Loudness.h"
#include "Tone.h"
#include "TrackProcessing.h"
#include "Wav.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioEngine";
constexpr int kSampleRate = kProjectSampleRate;
// Master stage of every mix (Play, Record, Share): loudness target, peak ceiling and boost cap.
constexpr float kMasterLufs = -14.0f;
constexpr float kMasterCeilingDb = -1.0f;
constexpr float kMasterMaxGainDb = 24.0f;

/** Removes the recorded click and, with a reference, the speaker bleed (see BleedRemoval.h). */
void cleanTake(const TrackSource &source, std::vector<float> &samples) {
    // Preserve PCM clipping bounds if click removal precedes the bleed estimator.
    const bool needsRaw = source.metronomeBpm > 0.0f && !source.referencePath.empty();
    const std::vector<float> raw = needsRaw ? samples : std::vector<float>{};
    // Click first: the reference holds the mix without the click, so the bleed model must not see it.
    if (source.metronomeBpm > 0.0f) {
        float removedDb = 0.0f;
        const char *method = "count-in template";
        if (!removeMetronomeWithTemplate(samples, source.offsetFrames, source.countInFrames, source.metronomeBpm,
                                         kSampleRate, removedDb)) {
            method = "linear model";
            removedDb = removeMetronome(samples, source.offsetFrames, source.metronomeBpm, source.metronomeVolume,
                                        kSampleRate);
        }
        MYAUDIO_LOGI(kTag, "Metronome removal (%s) %s: %.1f dB around the beats", method, source.path.c_str(),
                     removedDb);
    }
    if (source.referencePath.empty()) return;

    const auto start = std::chrono::steady_clock::now();
    std::vector<int16_t> pcm;
    int sampleRate = 0;
    if (!readWavMono16(source.referencePath, pcm, sampleRate) || sampleRate != kSampleRate) {
        MYAUDIO_LOGW(kTag, "Missing reference %s", source.referencePath.c_str());
        return;
    }
    std::vector<float> reference(pcm.size());
    for (size_t i = 0; i < pcm.size(); ++i) reference[i] = pcm[i] / 32768.0f;
    // Take index j holds what the speaker played at reference index j - latency.
    const int64_t latency = -source.offsetFrames - source.countInFrames;
    float removedDb = 0.0f;
    if (!removeBleed(samples, reference, latency, removedDb, needsRaw ? &raw : nullptr)) return;
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start);
    MYAUDIO_LOGI(kTag, "Bleed removal %s: %.2f dB of the take in %lld ms", source.path.c_str(), removedDb,
                 static_cast<long long>(elapsed.count()));

    if (!source.cachePath.empty()) {
        std::vector<int16_t> cleaned(samples.size());
        for (size_t i = 0; i < samples.size(); ++i) cleaned[i] = toPcm16(samples[i]);
        WavWriter cache;
        if (cache.open(source.cachePath, kSampleRate)) {
            cache.write(cleaned.data(), cleaned.size());
            cache.finish();
        }
    }
}

/** Reads a take (or its cache), removes click and bleed and silences it before project frame 0. */
bool loadCleanTake(const TrackSource &source, std::vector<int16_t> &pcm, std::vector<float> &samples,
                   std::vector<float> &right) {
    int sampleRate = 0;
    pcm.clear();
    int channels = 0;
    right.clear();
    if (!readWav16(source.path, pcm, sampleRate, channels) || sampleRate != kSampleRate) {
        MYAUDIO_LOGW(kTag, "Skipping unreadable track %s", source.path.c_str());
        return false;
    }
    samples.resize(pcm.size() / channels);
    if (channels == 2) right.resize(samples.size());
    for (size_t i = 0; i < samples.size(); ++i) {
        samples[i] = pcm[i * channels] / 32768.0f;
        if (channels == 2) right[i] = pcm[i * channels + 1] / 32768.0f;
    }

    // Stereo files are imports, never microphone takes: no click/bleed model or mono cache applies.
    if (channels == 2) {
        const auto start = static_cast<size_t>(std::clamp<int64_t>(-source.offsetFrames, 0, samples.size()));
        std::fill(samples.begin(), samples.begin() + start, 0.0f);
        std::fill(right.begin(), right.begin() + start, 0.0f);
        return true;
    }

    // The cleaned take only depends on the immutable take and reference files, so the costly
    // bleed removal runs once and is cached next to them.
    bool cleaned = false;
    if (!source.cachePath.empty()) {
        int cacheRate = 0;
        pcm.clear();
        if (readWavMono16(source.cachePath, pcm, cacheRate) && cacheRate == kSampleRate && pcm.size() == samples.size()) {
            for (size_t i = 0; i < pcm.size(); ++i) samples[i] = pcm[i] / 32768.0f;
            cleaned = true;
        }
    }
    if (!cleaned) cleanTake(source, samples);

    // Latency and count-in lie before project frame 0. Silence them before the effects, so the
    // count-in neither sets the normalisation peak nor rings into the song through the reverb.
    const auto projectStart = static_cast<size_t>(std::clamp<int64_t>(-source.offsetFrames, 0, samples.size()));
    std::fill(samples.begin(), samples.begin() + static_cast<std::ptrdiff_t>(projectStart), 0.0f);
    return true;
}

/**
 * Brings [left]/[right] back to the loudness [targetLufs] they had before an effect. Silence on
 * either side leaves the signal as it is; the correction is capped so a near-silent effect output
 * is not lifted into noise.
 */
void matchLoudness(std::vector<float> &left, std::vector<float> &right, double targetLufs) {
    constexpr double kMaxCorrectionDb = 24.0;
    if (!std::isfinite(targetLufs)) return;
    const double after = integratedLoudnessStreamed(left, right);
    if (!std::isfinite(after)) return;
    const double correctionDb = std::clamp(targetLufs - after, -kMaxCorrectionDb, kMaxCorrectionDb);
    const auto gain = static_cast<float>(std::pow(10.0, correctionDb / 20.0));
    for (float &sample : left) sample *= gain;
    for (float &sample : right) sample *= gain;
}

}  // namespace

int16_t toPcm16(float sample) {
    const float clipped = std::clamp(sample, -1.0f, 1.0f);
    return static_cast<int16_t>(std::lrintf(clipped * 32767.0f));
}

std::vector<EffectSlot> decodeEffectChain(const float *values, size_t count) {
    std::vector<EffectSlot> chain;
    size_t index = 0;
    while (index + 2 <= count) {
        const auto type = static_cast<int>(values[index]);
        const auto parameters = static_cast<size_t>(std::max(0.0f, values[index + 1]));
        index += 2;
        if (index + parameters > count) break;
        const bool tone = type == static_cast<int>(EffectType::Tone) && parameters == 4;
        const bool reverb = type == static_cast<int>(EffectType::Reverb) && parameters == 4;
        const bool airwindows = (type == static_cast<int>(EffectType::Airwindows) ||
                                 type == static_cast<int>(EffectType::AirwindowsLevelMatched)) && parameters >= 1;
        const bool amp = (type == static_cast<int>(EffectType::AmpModel) ||
                          type == static_cast<int>(EffectType::AmpModelLevelMatched)) && parameters == 3;
        const bool cabinet = (type == static_cast<int>(EffectType::Cabinet) ||
                              type == static_cast<int>(EffectType::CabinetLevelMatched)) && parameters == 1;
        if (tone || reverb || airwindows || amp || cabinet) {
            chain.push_back({static_cast<EffectType>(type), std::vector<float>(values + index, values + index + parameters)});
        } else {
            MYAUDIO_LOGW(kTag, "Skipping unknown effect %d with %zu parameters", type, parameters);
        }
        index += parameters;
    }
    return chain;
}

void applyEffectChain(const std::vector<EffectSlot> &chain, std::vector<float> &left, std::vector<float> &right,
                      bool &stereo, int sampleRate) {
    for (const EffectSlot &slot : chain) {
        const std::vector<float> &p = slot.parameters;
        switch (slot.type) {
            case EffectType::Tone:
                // EQ, then compressor; the stereo compressor links both channels so the image stays put.
                if (!std::isnan(p[0])) {
                    applyEq(left, {p[0], p[1], p[2]}, sampleRate);
                    if (stereo) applyEq(right, {p[0], p[1], p[2]}, sampleRate);
                }
                if (!std::isnan(p[3])) {
                    if (stereo) applyStereoCompressor(left, right, p[3], sampleRate);
                    else applyCompressor(left, p[3], sampleRate);
                }
                break;
            case EffectType::Reverb:
                // A mono signal becomes stereo here; stereo input keeps separate dry and wet paths.
                applyReverb(left, right, {p[0], p[1], p[2], p[3]}, sampleRate, stereo);
                stereo = true;
                break;
            case EffectType::Airwindows:
            case EffectType::AirwindowsLevelMatched: {
                const bool match = slot.type == EffectType::AirwindowsLevelMatched;
                // Measured as it will be heard: a mono signal counts on both channels, like its output.
                const double before = match ? integratedLoudnessStreamed(left, stereo ? right : left)
                                            : -std::numeric_limits<double>::infinity();
                // Airwindows effects are stereo in and out; a mono signal enters on both channels.
                if (!stereo) right = left;
                const std::vector<float> values(p.begin() + 1, p.end());
                if (applyAirwindows(static_cast<int>(p[0]), values, left, right, sampleRate)) {
                    stereo = true;
                    if (match) matchLoudness(left, right, before);
                } else {
                    MYAUDIO_LOGW(kTag, "Skipping unknown Airwindows effect %d", static_cast<int>(p[0]));
                    if (!stereo) right.clear();
                }
                break;
            }
            case EffectType::AmpModel:
            case EffectType::AmpModelLevelMatched:
            case EffectType::Cabinet:
            case EffectType::CabinetLevelMatched: {
                const bool cabinet = slot.type == EffectType::Cabinet || slot.type == EffectType::CabinetLevelMatched;
                const bool match = slot.type == EffectType::AmpModelLevelMatched || slot.type == EffectType::CabinetLevelMatched;
                // The same streaming processor as live monitoring, so the render sounds exactly like it.
                auto processor = ToneProcessor::create(cabinet ? ToneKind::Cabinet : ToneKind::AmpModel,
                                                       static_cast<int>(p[0]), cabinet ? 0.0f : p[1],
                                                       cabinet ? 0.0f : p[2], stereo, sampleRate);
                if (!processor) {
                    MYAUDIO_LOGW(kTag, "Skipping missing tone %d", static_cast<int>(p[0]));
                    break;
                }
                const double before = match ? integratedLoudnessStreamed(left, stereo ? right : left)
                                            : -std::numeric_limits<double>::infinity();
                const size_t tail = static_cast<size_t>(processor->tailFrames());
                left.resize(left.size() + tail, 0.0f);
                if (stereo) right.resize(left.size(), 0.0f);
                processor->process(left.data(), stereo ? right.data() : nullptr, static_cast<int>(left.size()));
                if (match) {
                    if (stereo) {
                        matchLoudness(left, right, before);
                    } else {
                        // Measured on both channels like the input, then applied to the mono signal.
                        const double after = integratedLoudnessStreamed(left, left);
                        if (std::isfinite(before) && std::isfinite(after)) {
                            const auto gain = static_cast<float>(std::pow(10.0, std::clamp(before - after, -24.0, 24.0) / 20.0));
                            for (float &sample : left) sample *= gain;
                        }
                    }
                }
                break;
            }
        }
    }
}

bool loadProcessedTake(const TrackSource &source, std::vector<float> &left, std::vector<float> &right) {
    std::vector<int16_t> pcm;
    if (!loadCleanTake(source, pcm, left, right)) return false;
    if (!std::isnan(source.normalizeDb)) {
        if (!right.empty()) normalizeStereoPeak(left, right, source.normalizeDb);
        else normalizePeak(left, source.normalizeDb);
    }
    return true;
}

void applyMasterStage(std::vector<float> &left, std::vector<float> &right) {
    masterLoudness(left, right, kMasterLufs, kMasterCeilingDb, kMasterMaxGainDb, kSampleRate);
}

void renderProjectMix(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master,
                      std::vector<float> &mixLeft, std::vector<float> &mixRight) {
    const auto renderStart = std::chrono::steady_clock::now();
    mixTracks(sources, mixLeft, mixRight);
    // Master chain on the summed mix, then -14 LUFS with a -1 dBFS limiter; quiet projects are lifted by at most 24 dB.
    if (!mixLeft.empty()) {
        bool stereo = true;
        applyEffectChain(master, mixLeft, mixRight, stereo, kSampleRate);
    }
    applyMasterStage(mixLeft, mixRight);
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - renderStart);
    MYAUDIO_LOGI(kTag, "Rendered stereo mix of %zu tracks (%.1f s) in %lld ms", sources.size(),
                 static_cast<double>(mixLeft.size()) / kSampleRate, static_cast<long long>(elapsed.count()));
}

void mixTracks(const std::vector<TrackSource> &sources, std::vector<float> &mixLeft, std::vector<float> &mixRight) {
    mixLeft.clear();
    mixRight.clear();
    std::vector<float> samples;
    std::vector<float> wetRight;
    for (const TrackSource &source : sources) {
        if (!loadProcessedTake(source, samples, wetRight)) continue;
        const bool stereoInput = !wetRight.empty();
        bool stereo = stereoInput;
        applyEffectChain(source.effects, samples, wetRight, stereo, kSampleRate);
        // A mono track without stereo effect plays the same signal on both channels.
        const std::vector<float> &right = stereo ? wetRight : samples;
        float panLeft = 1.0f;
        float panRight = 1.0f;
        if (stereoInput) balanceGains(source.pan, panLeft, panRight);
        else panGains(source.pan, panLeft, panRight);

        // Frames before project start (the compensated latency) are dropped.
        const int64_t end = source.offsetFrames + static_cast<int64_t>(samples.size());
        if (end > static_cast<int64_t>(mixLeft.size())) {
            mixLeft.resize(static_cast<size_t>(std::max<int64_t>(end, 0)), 0.0f);
            mixRight.resize(mixLeft.size(), 0.0f);
        }
        const int64_t first = std::max<int64_t>(0, -source.offsetFrames);
        size_t segment = 0;
        for (int64_t index = first; index < static_cast<int64_t>(samples.size()); ++index) {
            const int64_t frame = source.offsetFrames + index;
            const float gain = source.gain * envelopeGain(source.volumeEnvelope, frame, segment);
            mixLeft[frame] += gain * panLeft * samples[index];
            mixRight[frame] += gain * panRight * right[index];
        }
    }
}

bool writeStems(const std::vector<TrackSource> &sources, int64_t projectFrames, const std::vector<std::string> &targets) {
    if (sources.size() != targets.size()) return false;
    std::vector<int16_t> pcm;
    std::vector<float> samples;
    std::vector<float> right;
    const size_t frames = static_cast<size_t>(std::max<int64_t>(projectFrames, 0));
    std::vector<int16_t> stem;
    for (size_t t = 0; t < sources.size(); ++t) {
        const TrackSource &source = sources[t];
        if (!loadCleanTake(source, pcm, samples, right)) return false;
        const int channels = right.empty() ? 1 : 2;
        stem.resize(frames * channels);
        // Project frame n of every stem is sample n, so the files line up in any DAW.
        for (int64_t frame = 0; frame < projectFrames; ++frame) {
            const int64_t index = frame - source.offsetFrames;
            const bool inTake = index >= 0 && index < static_cast<int64_t>(samples.size());
            stem[frame * channels] = inTake ? toPcm16(samples[index]) : 0;
            if (channels == 2) stem[frame * channels + 1] = inTake ? toPcm16(right[index]) : 0;
        }
        WavWriter writer;
        if (!writer.open(targets[t], kSampleRate, channels)) return false;
        writer.write(stem.data(), frames);
        writer.finish();
    }
    return true;
}

}  // namespace myaudio
