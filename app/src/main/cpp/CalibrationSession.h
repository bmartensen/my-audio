#pragma once

#include "InputChannel.h"
#include <cmath>

namespace myaudio {

enum SessionStatus {
    kOk = 0,
    kOpenOutputFailed = -1,
    kOpenInputFailed = -2,
    kStartFailed = -3,
    kTimeout = -4,
    kNoSignal = -5,
};

/** Outcome of one full-duplex calibration session. JniBridge serialises it in field order. */
struct SessionResult {
    int status = kOk;
    int sampleRate = 0;
    int validChirps = 0;
    double latencyFrames = 0.0;       // median correlation lag between played and recorded sweep
    double chirpSpreadFrames = 0.0;   // max - min lag of the valid sweeps
    double minSnrDb = 0.0;
    double timestampLatencyFrames = NAN;  // same quantity estimated from stream timestamps
    int xruns = 0;
    bool outputMMap = false;
    bool inputMMap = false;
    int framesPerBurst = 0;
    int outputBufferFrames = 0;
};

/**
 * Plays sweeps through the selected output while recording the selected microphone in one
 * full-duplex stream pair and measures the round-trip latency. Blocks for about 3 seconds.
 */
SessionResult runCalibrationSession(int outputDeviceId, int inputDeviceId, bool bluetooth, bool handsFreeInput,
                                    InputChannel inputChannel);

}  // namespace myaudio
