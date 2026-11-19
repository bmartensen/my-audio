#pragma once

#include <cstdint>
#include <vector>

namespace myaudio {

/**
 * Non-destructive per-track processing applied while rendering the mix, in this order:
 * metronome removal -> normalisation -> reverb (the caller applies the track volume last).
 */

/**
 * Estimates how the metronome click reached the microphone (least-squares FIR from the known
 * click signal) and subtracts it. [offsetFrames] maps sample 0 to its project frame.
 * Returns the removed click energy relative to the signal around the beats in dB.
 */
float removeMetronome(std::vector<float> &samples, int64_t offsetFrames, float bpm, float volume, int sampleRate);

/**
 * Template subtraction for takes with a count-in: the clicks recorded during the count-in
 * (before project frame 0, nothing else playing) are averaged per click type and subtracted at
 * every beat of the song, after one global gain/shift fit per type. Captures room tail and
 * repeatable speaker non-linearity that the linear model misses. Returns false without touching
 * the samples if the count-in is unusable (then use removeMetronome()); otherwise [removedDb]
 * receives the removed energy around the beats.
 */
bool removeMetronomeWithTemplate(std::vector<float> &samples, int64_t offsetFrames, int64_t countInFrames,
                                 float bpm, int sampleRate, float &removedDb);

/** Scales the track so its peak reaches [targetDb] dBFS. */
void normalizePeak(std::vector<float> &samples, float targetDb);

struct ReverbParameters {
    float seconds;     // RT60 at low frequencies, 0.2 (small chamber) .. 10 (cathedral)
    float preDelayMs;  // gap before the first reflection
    float damping;     // 0 = highs decay as long as lows (stone), 1 = highs decay 5x faster (soft room)
    float mix;         // wet/dry energy ratio, independent of the decay time
};

/**
 * Algorithmic stereo reverb: pre-delay, input diffusion, early reflections and a 16-line
 * feedback delay network (Householder matrix) with per-line absorption filters for
 * frequency-dependent decay. With stereoInput, each input channel keeps its own dry and wet path;
 * otherwise the mono input in [left] feeds both outputs. Extends the channels by the reverb tail.
 */
void applyReverb(std::vector<float> &left, std::vector<float> &right, const ReverbParameters &parameters,
                 int sampleRate, bool stereoInput = false);

}  // namespace myaudio
