#include "Metronome.h"

namespace myaudio {
namespace {

constexpr int kClickSampleRate = 48000;

/** Short decaying sine burst at full scale; the metronome volume is applied by the caller. */
std::vector<float> makeClick(double frequency) {
    const int length = kClickSampleRate * 30 / 1000;
    std::vector<float> click(length);
    for (int n = 0; n < length; ++n) {
        const double t = static_cast<double>(n) / kClickSampleRate;
        click[n] = static_cast<float>(std::sin(2.0 * M_PI * frequency * t) * std::exp(-t * 150.0));
    }
    return click;
}

}  // namespace

const std::vector<float> &ClickGrid::click(bool accent) {
    static const std::vector<float> regular = makeClick(1500.0);
    static const std::vector<float> accented = makeClick(2500.0);
    return accent ? accented : regular;
}

}  // namespace myaudio
