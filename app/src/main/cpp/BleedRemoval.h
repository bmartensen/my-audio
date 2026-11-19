#pragma once

#include <cstdint>
#include <vector>

namespace myaudio {

/**
 * Removes the playback that leaked from the speaker into a take recorded without headphones.
 * [reference] is exactly what the engine played during the take (the mix without the click),
 * indexed like the take file; [latencyFrames] is the calibrated round trip. A short least-squares
 * FIR (384 taps, ~8 ms: speaker, direct path and residual offset) is estimated from up to 60 s of
 * the take and the modelled leak is subtracted from all of it. Clipped PCM observations are
 * treated as bounds during estimation, and the predicted leak follows that saturation.
 * A slowly interpolated gain follows level changes only where playback strongly explains
 * the microphone signal; this does not gate or mute the new performance.
 * Pass [rawSamples] if click removal already modified the input, so clipping bounds stay valid.
 * Returns false if there is nothing to remove; otherwise
 * [removedDb] receives the energy reduction of the take.
 */
bool removeBleed(std::vector<float> &samples, const std::vector<float> &reference, int64_t latencyFrames,
                 float &removedDb, const std::vector<float> *rawSamples = nullptr);

}  // namespace myaudio
