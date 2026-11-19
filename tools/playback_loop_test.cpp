#include <cassert>
#include <cstdio>

#include "../app/src/main/cpp/PlaybackLoop.h"

int main() {
    myaudio::PlaybackLoop loop;
    assert(!loop.range().active());
    assert(loop.range().wrap(5000) == 5000);

    loop.set(1000, 2000);
    const auto range = loop.range();
    assert(range.active());
    assert(range.wrap(1999) == 1999);
    // The frame at the end is never played: playback continues at the start, sample-exactly.
    assert(range.wrap(2000) == 1000);
    // A seek behind the loop (e.g. tapping a waveform) also returns into it.
    assert(range.wrap(3500) == 1000);
    // Before the start the loop does not interfere; playback runs into it.
    assert(range.wrap(10) == 10);

    // Inverted or empty ranges switch the loop off instead of trapping playback.
    loop.set(2000, 2000);
    assert(!loop.range().active());
    loop.set(3000, 1000);
    assert(!loop.range().active());

    loop.set(0, 480);
    loop.clear();
    assert(!loop.range().active());
    std::puts("Playback loop: wrap at end, seek behind, inactive ranges and clear passed");
    return 0;
}
