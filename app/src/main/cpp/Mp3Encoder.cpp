#include "Mp3Encoder.h"

#include <algorithm>

#include <lame.h>

namespace myaudio {

namespace {

/** Frames per encoder call: a multiple of the 1152-sample MP3 frame. */
constexpr int kChunkFrames = 1152 * 8;

struct LameHandle {
    lame_t lame = lame_init();
    ~LameHandle() {
        if (lame != nullptr) lame_close(lame);
    }
};

}  // namespace

std::vector<unsigned char> encodeMp3(const float *samples, size_t frames, int channels, int sampleRate) {
    if (samples == nullptr || channels < 1 || channels > 2 || sampleRate <= 0) return {};
    LameHandle handle;
    lame_t lame = handle.lame;
    if (lame == nullptr) return {};
    lame_set_in_samplerate(lame, sampleRate);
    lame_set_out_samplerate(lame, sampleRate);
    lame_set_num_channels(lame, channels);
    lame_set_mode(lame, channels == 2 ? JOINT_STEREO : MONO);
    lame_set_VBR(lame, vbr_default);
    lame_set_VBR_quality(lame, 2.0f);
    lame_set_write_id3tag_automatic(lame, 0);
    lame_set_bWriteVbrTag(lame, 1);
    if (lame_init_params(lame) < 0) return {};

    // LAME's documented worst case: 1.25 × samples + 7200 bytes per call.
    std::vector<unsigned char> buffer(kChunkFrames * 5 / 4 + 7200);
    std::vector<unsigned char> mp3;
    mp3.reserve(frames / 4);
    for (size_t done = 0; done < frames;) {
        const int count = static_cast<int>(std::min<size_t>(kChunkFrames, frames - done));
        const float *chunk = samples + done * channels;
        // Mono passes its channel twice: LAME reads the right input even when it encodes one channel.
        const int bytes = channels == 2
                ? lame_encode_buffer_interleaved_ieee_float(lame, chunk, count, buffer.data(), static_cast<int>(buffer.size()))
                : lame_encode_buffer_ieee_float(lame, chunk, chunk, count, buffer.data(), static_cast<int>(buffer.size()));
        if (bytes < 0) return {};
        mp3.insert(mp3.end(), buffer.begin(), buffer.begin() + bytes);
        done += static_cast<size_t>(count);
    }
    const int bytes = lame_encode_flush(lame, buffer.data(), static_cast<int>(buffer.size()));
    if (bytes < 0) return {};
    mp3.insert(mp3.end(), buffer.begin(), buffer.begin() + bytes);

    // The encoder reserved the first frame for the LAME tag; it is complete only now.
    unsigned char tag[2880];
    const size_t tagSize = lame_get_lametag_frame(lame, tag, sizeof tag);
    if (tagSize > 0 && tagSize <= sizeof tag && tagSize <= mp3.size()) std::copy(tag, tag + tagSize, mp3.begin());
    return mp3;
}

}  // namespace myaudio
