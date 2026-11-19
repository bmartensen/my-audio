#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "VolumeEnvelope.h"

namespace myaudio {

/** Fixed engine and project sample rate. */
constexpr int kProjectSampleRate = 48000;

/** Effect type codes; must match the codes in AudioEngine.encodeChain (Kotlin). */
enum class EffectType : int {
    Tone = 1,    // "EQ (My Audio)": eqLowDb (NaN = EQ off), eqPresenceDb, eqHighDb, compressorAmount (NaN = off)
    Reverb = 2,  // "Hall (My Audio)": seconds, preDelayMs, damping, mix
    Airwindows = 3,  // catalog index (AirwindowsEffects.h), then the effect's parameter values 0..1
    // Same layout as Airwindows; the output is brought back to the input's integrated loudness, so
    // switching the effect on changes the sound but not the balance of the mix.
    AirwindowsLevelMatched = 4,
};

/** One switched-on insert of an effect chain. */
struct EffectSlot {
    EffectType type;
    std::vector<float> parameters;
};

/** Decodes a chain encoded per slot as type code, parameter count and parameters; malformed slots are dropped. */
std::vector<EffectSlot> decodeEffectChain(const float *values, size_t count);

/** One track and its non-destructive processing, in this order: cleanup, normalisation, [effects], volume/pan. */
struct TrackSource {
    std::string path;
    std::string referencePath;  // what was played while recording; empty = no bleed removal
    std::string cachePath;      // cleaned take (click + bleed removed) to reuse; empty = no cache
    int64_t offsetFrames;  // project frame of the file's first sample
    int64_t countInFrames; // metronome count-in recorded before project frame 0, 0 = none
    float gain;
    float metronomeBpm;    // > 0: remove the click recorded at this tempo ...
    float metronomeVolume; // ... and volume
    float normalizeDb;     // peak target in dBFS, NaN = off
    float pan;             // -1 left .. 1 right
    std::vector<VolumePoint> volumeEnvelope;
    std::vector<EffectSlot> effects;  // in signal order
};

/**
 * Runs [chain] in order over a signal in [left] (and [right] when [stereo]). A mono signal becomes
 * stereo through the reverb or an Airwindows effect; [stereo] reports the result. Both may extend the
 * channels by their tail.
 */
void applyEffectChain(const std::vector<EffectSlot> &chain, std::vector<float> &left, std::vector<float> &right,
                      bool &stereo, int sampleRate);

/**
 * Offline part of the engine, shared by Android and desktop: renders the stereo project mix with
 * the [master] chain and the fixed loudness stage into [left]/[right] (index = project frame).
 * Unreadable tracks are skipped.
 */
void renderProjectMix(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master,
                      std::vector<float> &left, std::vector<float> &right);

/**
 * A take as the mix starts from it: click and bleed removed, silent before project frame 0 and
 * normalised; index = take sample (project frame - offsetFrames). [right] stays empty for mono.
 */
bool loadProcessedTake(const TrackSource &source, std::vector<float> &left, std::vector<float> &right);

/** The sum of [sources] with their effects, volume and pan, before the master chain (index = project frame). */
void mixTracks(const std::vector<TrackSource> &sources, std::vector<float> &left, std::vector<float> &right);

/** The fixed stage after the master chain: -14 LUFS with a -1 dBFS limiter. */
void applyMasterStage(std::vector<float> &left, std::vector<float> &right);

/**
 * Writes each take as a mono/stereo WAV from project frame 0 to [projectFrames]: click and bleed
 * removed, latency compensated, without effects, volume or pan.
 */
bool writeStems(const std::vector<TrackSource> &sources, int64_t projectFrames, const std::vector<std::string> &targets);

int16_t toPcm16(float sample);

}  // namespace myaudio
