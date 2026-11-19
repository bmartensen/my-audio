#pragma once

#include <atomic>

namespace myaudio {

/** Discards whole duplex blocks while a Bluetooth link settles. No project frames pass yet. */
class StreamWarmup {
public:
    static constexpr int kBluetoothSeconds = 1;

    void reset(int frames) {
        mRemaining = frames;
        mReady.store(frames == 0, std::memory_order_relaxed);
    }

    /** Called only by the audio callback, before writing output or capturing input. */
    bool discard(int frames) {
        if (mRemaining > 0) {
            mRemaining -= frames;
            return true;
        }
        mReady.store(true, std::memory_order_release);
        return false;
    }

    bool ready() const { return mReady.load(std::memory_order_acquire); }

private:
    int mRemaining = 0;
    std::atomic<bool> mReady{true};
};

} // namespace myaudio
