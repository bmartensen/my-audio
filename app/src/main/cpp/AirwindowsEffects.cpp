#include "AirwindowsEffects.h"

#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <unordered_map>

#include "AirwindowsCatalog.inc"

namespace myaudio {
namespace {

using Factory = std::unique_ptr<AirwinConsolidatedBase> (*)();

struct Entry {
    AirwindowsInfo info;
    int parameters;
    Factory create;
};

#define MYAUDIO_AIRWINDOWS_ENTRY(Name, category, description)                                              \
    Entry{{#Name, category, description}, airwinconsolidated::Name::kNumParameters,                        \
          []() -> std::unique_ptr<AirwinConsolidatedBase> {                                                \
              return std::make_unique<airwinconsolidated::Name::Name>(0);                                  \
          }},

const std::vector<Entry> &entries() {
    static const std::vector<Entry> table = {AIRWINDOWS_CATALOG(MYAUDIO_AIRWINDOWS_ENTRY)};
    return table;
}

#undef MYAUDIO_AIRWINDOWS_ENTRY

constexpr int kSampleRate = 48000;
// Long enough for the big Airwindows halls to fade below the trim threshold.
constexpr double kTailSeconds = 8.0;
// About -120 dBFS; Airwindows' own floating-point dither stays far below it.
constexpr float kSilence = 1e-6f;
constexpr int kBlock = 512;

/** A new instance at the project rate with all parameters at their defaults. */
std::unique_ptr<AirwinConsolidatedBase> create(int index, int sampleRate) {
    if (index < 0 || index >= static_cast<int>(entries().size())) return nullptr;
    // Some effects read the rate in their constructor.
    AirwinConsolidatedBase::defaultSampleRate = static_cast<float>(sampleRate);
    auto effect = entries()[index].create();
    effect->setSampleRate(static_cast<float>(sampleRate));
    return effect;
}

int parameterCount(int index) {
    return index >= 0 && index < static_cast<int>(entries().size()) ? entries()[index].parameters : 0;
}

std::mutex displayLock;

}  // namespace

const std::vector<AirwindowsInfo> &airwindowsCatalog() {
    static const std::vector<AirwindowsInfo> catalog = [] {
        std::vector<AirwindowsInfo> result;
        for (const Entry &entry : entries()) result.push_back(entry.info);
        return result;
    }();
    return catalog;
}

AirwindowsParameters airwindowsParameters(int index) {
    AirwindowsParameters result;
    const auto effect = create(index, kSampleRate);
    if (!effect) return result;
    char name[256];
    for (int i = 0; i < parameterCount(index); ++i) {
        name[0] = '\0';
        effect->getParameterName(i, name);
        result.names.emplace_back(name);
        result.defaults.push_back(std::clamp(effect->getParameter(i), 0.0f, 1.0f));
    }
    return result;
}

std::vector<std::string> airwindowsDisplay(int index, const std::vector<float> &values) {
    // One cached instance per effect: some allocate large delay buffers.
    static std::unordered_map<int, std::unique_ptr<AirwinConsolidatedBase>> instances;
    std::lock_guard<std::mutex> guard(displayLock);
    std::vector<std::string> result;
    auto &effect = instances[index];
    if (!effect) effect = create(index, kSampleRate);
    if (!effect) return result;
    const int count = parameterCount(index);
    char text[256];
    char label[256];
    for (int i = 0; i < count; ++i) {
        if (i < static_cast<int>(values.size())) effect->setParameter(i, std::clamp(values[i], 0.0f, 1.0f));
    }
    for (int i = 0; i < count; ++i) {
        text[0] = label[0] = '\0';
        effect->getParameterDisplay(i, text);
        effect->getParameterLabel(i, label);
        std::string value(text);
        // Airwindows pads numbers to a fixed width.
        value.erase(0, value.find_first_not_of(' '));
        if (label[0] != '\0') value += std::string(" ") + label;
        result.push_back(value);
    }
    return result;
}

bool applyAirwindows(int index, const std::vector<float> &values, std::vector<float> &left,
                     std::vector<float> &right, int sampleRate) {
    auto effect = create(index, sampleRate);
    if (!effect) return false;
    const int count = parameterCount(index);
    for (int i = 0; i < count && i < static_cast<int>(values.size()); ++i) {
        effect->setParameter(i, std::clamp(values[i], 0.0f, 1.0f));
    }
    const size_t dryLength = left.size();
    const size_t total = dryLength + static_cast<size_t>(kTailSeconds * sampleRate);
    left.resize(total, 0.0f);
    right.resize(total, 0.0f);
    std::vector<float> outLeft(kBlock);
    std::vector<float> outRight(kBlock);
    for (size_t start = 0; start < total; start += kBlock) {
        const auto frames = static_cast<int>(std::min<size_t>(kBlock, total - start));
        float *inputs[2] = {left.data() + start, right.data() + start};
        float *outputs[2] = {outLeft.data(), outRight.data()};
        effect->processReplacing(inputs, outputs, frames);
        std::copy_n(outLeft.data(), frames, left.data() + start);
        std::copy_n(outRight.data(), frames, right.data() + start);
    }
    size_t end = total;
    while (end > dryLength && std::fabs(left[end - 1]) < kSilence && std::fabs(right[end - 1]) < kSilence) --end;
    left.resize(end);
    right.resize(end);
    return true;
}

}  // namespace myaudio
