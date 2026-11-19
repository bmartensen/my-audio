#pragma once

#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

namespace myaudio {

/** Reads a mono 16-bit PCM WAV file. Returns false for missing files or other formats. */
bool readWavMono16(const std::string &path, std::vector<int16_t> &samples, int &sampleRate);

/** Reads interleaved mono/stereo PCM16; samples.size() / channels is the frame count. */
bool readWav16(const std::string &path, std::vector<int16_t> &samples, int &sampleRate, int &channels);

/** Streams interleaved PCM16 to a WAV file; sizes in the header are patched by finish(). */
class WavWriter {
public:
    ~WavWriter();
    bool open(const std::string &path, int sampleRate, int channels = 1);
    /** Writes count audio frames (count * channels samples). */
    bool write(const int16_t *samples, size_t count);
    /** Finalises the header and closes the file; returns the number of frames written. */
    int64_t finish();

private:
    FILE *mFile = nullptr;
    int64_t mFrames = 0;
    int mChannels = 1;
};

}  // namespace myaudio
