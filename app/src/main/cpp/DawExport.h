#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "ProjectMix.h"

namespace myaudio {

/** Where one exported track sits and what was written for it. */
struct DawTrack {
    int64_t start = 0;      // project frame of the clip's first sample
    int64_t dryFrames = 0;  // frames of the dry file
    int64_t wetFrames = 0;  // frames of the rendered file, 0 = none written
    int channels = 1;       // of both files: stereo once the take or its rendered effects are stereo
};

/**
 * Exports every source for a DAW session (SoundCraft): the dry take as the mix starts from it (cleaned,
 * normalised, from project frame 0 or its later start, count-in and latency cut off) and, for a non-empty
 * [renderChains] entry, the same audio through that chain, including its tail. Both are 32-bit float WAVs
 * with the same channel count. Returns false if a file cannot be written; unreadable takes are skipped
 * (dryFrames 0).
 */
bool exportDawTracks(const std::vector<TrackSource> &sources, const std::vector<std::vector<EffectSlot>> &renderChains,
                     const std::vector<std::string> &dryPaths, const std::vector<std::string> &wetPaths,
                     std::vector<DawTrack> &tracks);

/** Integrated loudness (LUFS) of the mix of [sources] through [master], before the fixed master stage. */
double mixLoudness(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master);

/** Writes [frames] from [first] of [left] (and [right] for two channels) as a 32-bit float WAV. */
bool writeFloatWav(const std::string &path, const std::vector<float> &left, const std::vector<float> &right,
                   size_t first, size_t frames, int channels, int sampleRate);

}  // namespace myaudio
