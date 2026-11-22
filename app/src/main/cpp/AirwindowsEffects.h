#pragma once

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

struct AirwinConsolidatedBase;

namespace myaudio {

/** One Airwindows effect of the vendored catalog (third_party/airwindows, sorted by name). */
struct AirwindowsInfo {
    const char *name;
    const char *category;
    const char *description;  // the original one-line description
};

/** The catalog; an effect's index is stable for one build and is what the effect chain refers to. */
const std::vector<AirwindowsInfo> &airwindowsCatalog();

/** Parameter names and default values (0..1) of effect [index]; empty for an unknown index. */
struct AirwindowsParameters {
    std::vector<std::string> names;
    std::vector<float> defaults;
};
AirwindowsParameters airwindowsParameters(int index);

/** The effect's own text for each value, e.g. "-3.0 dB" or "Wide"; [values] are 0..1. */
std::vector<std::string> airwindowsDisplay(int index, const std::vector<float> &values);

/**
 * Runs effect [index] with [values] over the stereo signal [left]/[right] (equal length). The
 * channels are first extended by a few seconds of silence so delays and reverbs ring out, then
 * trailing silence is trimmed again, never below the original length. Returns false for an
 * unknown index without touching the signal.
 */
bool applyAirwindows(int index, const std::vector<float> &values, std::vector<float> &left,
                     std::vector<float> &right, int sampleRate);

/**
 * One running Airwindows effect for real-time use (live monitoring). Create and destroy it outside the
 * audio thread: construction allocates the effect and its scratch buffers; process() never allocates.
 */
class AirwindowsProcessor {
public:
    /** Null for an unknown [index]. */
    static std::unique_ptr<AirwindowsProcessor> create(int index, const std::vector<float> &values, int sampleRate);
    ~AirwindowsProcessor();

    /** Processes the stereo signal in place; any number of frames. */
    void process(float *left, float *right, int frames);

private:
    AirwindowsProcessor() = default;
    static constexpr int kBlock = 256;
    std::unique_ptr<AirwinConsolidatedBase> mEffect;
    std::vector<float> mOutLeft = std::vector<float>(kBlock);
    std::vector<float> mOutRight = std::vector<float>(kBlock);
};

}  // namespace myaudio
