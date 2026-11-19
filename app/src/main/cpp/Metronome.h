#pragma once

#include <cmath>
#include <cstdint>
#include <vector>

namespace myaudio {

/**
 * The metronome signal: beats on the project grid starting at frame 0, beat 1 of each 4/4 bar
 * accented. Both the live click and the offline click removal use this class, so the removal
 * reconstructs exactly what was played.
 */
class ClickGrid {
public:
    ClickGrid(float bpm, int sampleRate) : mFramesPerBeat(60.0 * sampleRate / bpm) {}

    int64_t beatStart(int64_t beat) const { return static_cast<int64_t>(std::ceil(beat * mFramesPerBeat)); }
    /** Index of the beat whose click may cover [frame]: the last beat starting at or before it. */
    int64_t beatAt(int64_t frame) const { return static_cast<int64_t>(std::floor(frame / mFramesPerBeat)); }
    static bool isAccent(int64_t beat) { return beat % 4 == 0; }
    static const std::vector<float> &click(bool accent);

private:
    double mFramesPerBeat;
};

}  // namespace myaudio
