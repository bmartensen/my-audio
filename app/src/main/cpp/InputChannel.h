#pragma once

namespace myaudio {

/**
 * Which channel of the input becomes the mono take; values match `InputChannel` in Kotlin.
 * [Device] opens the input in mono as before 0.8.1 (single-channel devices); every other
 * value opens it in stereo, so the latency is the same whichever channel is chosen.
 */
enum class InputChannel : int { Device = -1, First = 0, Second = 1, Both = 2 };

inline InputChannel toInputChannel(int value) {
    switch (value) {
        case 0: return InputChannel::First;
        case 1: return InputChannel::Second;
        case 2: return InputChannel::Both;
        default: return InputChannel::Device;
    }
}

/**
 * Mono sample of one interleaved input frame. [InputChannel::Both] sums at unity gain, so a single
 * connected source keeps its level; the input meter shows the sum, which is what is recorded.
 */
inline float monoInputSample(const float *frame, int channels, InputChannel choice) {
    if (channels < 2) return frame[0];
    switch (choice) {
        case InputChannel::Second: return frame[1];
        case InputChannel::Both: return frame[0] + frame[1];
        default: return frame[0];
    }
}

}  // namespace myaudio
