#pragma once

#include <memory>
#include <string>
#include <vector>

namespace myaudio {

/** Kind of an imported tone file; must match ToneKind in Kotlin. */
enum class ToneKind : int {
    AmpModel = 1,  // Neural Amp Modeler profile (.nam)
    Cabinet = 2,   // impulse response (WAV)
};

/** What a tone file holds, read without registering it (import validation). */
struct ToneFileInfo {
    bool ok = false;
    std::string error;     // why the file cannot be used
    double sampleRate = 0; // of the profile or the original IR
    int frames = 0;        // IR length after resampling to the project rate
    std::string metadata;  // NAM "metadata" object as JSON, empty for IRs
};

/** Checks a tone file of [kind]: a NAM profile must load and run at 48 kHz; an IR must be a readable WAV. */
ToneFileInfo inspectToneFile(const std::string &path, ToneKind kind);

/**
 * Loads a tone file once and returns its handle for effect chains (>= 0), or -1 if it cannot be used.
 * The same path returns the same handle; the files are immutable in the tone library.
 */
int loadTone(const std::string &path, ToneKind kind);

/**
 * One running amp model or cabinet for a mono or stereo signal, for offline rendering and live monitoring
 * alike, so both sound identical. Create it outside the audio thread; process() never allocates.
 * Both kinds add no latency: the cabinet convolves the first partition directly.
 */
class ToneProcessor {
public:
    /** Null for an unknown [handle] or a handle of another kind. Gains in dB; amp models only. */
    static std::unique_ptr<ToneProcessor> create(ToneKind kind, int handle, float inputDb, float outputDb,
                                                 bool stereo, int sampleRate);
    virtual ~ToneProcessor() = default;

    /** Processes [frames] of [left] (and [right] when created stereo) in place. */
    virtual void process(float *left, float *right, int frames) = 0;

    /** Frames the output rings on after the input ends (the IR length; 0 for amp models). */
    virtual int tailFrames() const = 0;
};

}  // namespace myaudio
