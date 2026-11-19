#pragma once

#include <atomic>
#include <cstdint>

namespace myaudio {

/** Practice loop of the playback; the audio callback wraps sample-exactly at its end. */
class PlaybackLoop {
public:
    /** [start, end) in project frames; an empty range switches the loop off. */
    void set(int64_t start, int64_t end) {
        // Publish an empty range first, so the callback never sees a new end with an old start.
        mEnd.store(-1, std::memory_order_relaxed);
        mStart.store(start, std::memory_order_relaxed);
        mEnd.store(end > start ? end : -1, std::memory_order_release);
    }

    void clear() { mEnd.store(-1, std::memory_order_release); }

    struct Range {
        int64_t start;
        int64_t end;
        bool active() const { return end > start; }
        /** Next frame to play; positions at or behind the end continue at the start. */
        int64_t wrap(int64_t position) const { return active() && position >= end ? start : position; }
    };

    /** Called once per audio block by the callback. */
    Range range() const {
        const int64_t end = mEnd.load(std::memory_order_acquire);
        return {mStart.load(std::memory_order_relaxed), end};
    }

private:
    std::atomic<int64_t> mStart{0};
    std::atomic<int64_t> mEnd{-1};
};

} // namespace myaudio
