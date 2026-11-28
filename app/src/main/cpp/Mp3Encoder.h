#pragma once

#include <cstddef>
#include <vector>

namespace myaudio {

/**
 * Encodes interleaved float [samples] (one or two channels, ±1 full scale) as an MP3 with LAME: VBR
 * quality 2 (about 190 kbit/s), joint stereo, and a LAME/Xing tag in the first frame so players know the
 * exact length and skip the encoder delay. No ID3 tag. Empty on failure.
 */
std::vector<unsigned char> encodeMp3(const float *samples, size_t frames, int channels, int sampleRate);

}  // namespace myaudio
