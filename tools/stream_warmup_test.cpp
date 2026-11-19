#include <cassert>
#include <iostream>

#include "../app/src/main/cpp/StreamWarmup.h"

int main() {
    myaudio::StreamWarmup warmup;
    warmup.reset(48000);
    int outputFrames = 0;
    int captureFrames = 0;
    // The complete final warmup block must be discarded in BOTH directions. In particular,
    // a non-divisible callback size must not start the reference before the take.
    for (int block = 0; block < 40; ++block) {
        if (warmup.discard(1536)) continue;
        outputFrames += 1536;
        captureFrames += 1536;
    }
    assert(warmup.ready());
    assert(outputFrames == 8 * 1536);
    assert(captureFrames == outputFrames);

    warmup.reset(48000);
    assert(!warmup.ready());
    warmup.reset(0);
    assert(warmup.ready());
    assert(!warmup.discard(192));
    std::cout << "Warmup: aligned capture/reference, whole-block boundary, reset and bypass passed\n";
}
