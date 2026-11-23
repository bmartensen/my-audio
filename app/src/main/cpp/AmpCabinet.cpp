#include "AmpCabinet.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <mutex>

#include "Log.h"
#include "NAM/dsp.h"
#include "NAM/get_dsp.h"
#include "json.hpp"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioTone";
constexpr double kRate = 48000.0;
// NAM processes at most this many frames per call; longer blocks are split.
constexpr int kAmpBlock = 1024;
// Cabinet partition: the first one is convolved directly (no latency), the rest by FFT once per partition.
constexpr int kPartition = 128;
// Longer impulse responses are cut; cabinets last well below this, room IRs belong into the reverb.
constexpr int kMaxIrFrames = 2 * 48000;

using Complex = std::complex<float>;

// ---------------------------------------------------------------------------------------------- WAV + resampling

/** A UTF-8 path (as JNI delivers it) on every platform, also with non-ASCII folder names on Windows. */
std::filesystem::path utf8Path(const std::string &path) { return std::filesystem::path(std::u8string(path.begin(), path.end())); }

uint32_t le32(const uint8_t *p) { return p[0] | p[1] << 8 | p[2] << 16 | static_cast<uint32_t>(p[3]) << 24; }
uint16_t le16(const uint8_t *p) { return static_cast<uint16_t>(p[0] | p[1] << 8); }

/** Reads any PCM (16/24/32 bit) or float (32/64 bit) WAV, mixing its channels to mono. */
bool readWavAnyMono(const std::string &path, std::vector<float> &mono, double &rate, std::string &error) {
    std::ifstream file(utf8Path(path), std::ios::binary);
    if (!file) { error = "cannot open file"; return false; }
    std::vector<uint8_t> data((std::istreambuf_iterator<char>(file)), {});
    if (data.size() < 12 || std::memcmp(data.data(), "RIFF", 4) != 0 || std::memcmp(data.data() + 8, "WAVE", 4) != 0) {
        error = "not a WAV file";
        return false;
    }
    int format = 0, channels = 0, bits = 0;
    size_t pos = 12;
    while (pos + 8 <= data.size()) {
        const uint32_t size = le32(&data[pos + 4]);
        const size_t body = pos + 8;
        if (body + size > data.size() && std::memcmp(&data[pos], "data", 4) != 0) break;
        if (std::memcmp(&data[pos], "fmt ", 4) == 0 && size >= 16) {
            format = le16(&data[body]);
            channels = le16(&data[body + 2]);
            rate = le32(&data[body + 4]);
            bits = le16(&data[body + 14]);
            if (format == 0xFFFE && size >= 26) format = le16(&data[body + 24]);  // WAVE_FORMAT_EXTENSIBLE
        } else if (std::memcmp(&data[pos], "data", 4) == 0) {
            if (channels <= 0 || rate <= 0) { error = "missing format"; return false; }
            const bool pcm = format == 1 && (bits == 16 || bits == 24 || bits == 32);
            const bool flt = format == 3 && (bits == 32 || bits == 64);
            if (!pcm && !flt) { error = "unsupported sample format"; return false; }
            const size_t bytes = bits / 8;
            const size_t available = std::min<size_t>(size, data.size() - body);
            const size_t frames = available / (bytes * channels);
            mono.assign(frames, 0.0f);
            for (size_t f = 0; f < frames; ++f) {
                double sum = 0.0;
                for (int c = 0; c < channels; ++c) {
                    const uint8_t *s = &data[body + (f * channels + c) * bytes];
                    double v = 0.0;
                    if (flt && bits == 32) { float x; std::memcpy(&x, s, 4); v = x; }
                    else if (flt) { double x; std::memcpy(&x, s, 8); v = x; }
                    else if (bits == 16) v = static_cast<int16_t>(le16(s)) / 32768.0;
                    else if (bits == 24) v = static_cast<int32_t>((s[0] << 8 | s[1] << 16 | static_cast<uint32_t>(s[2]) << 24)) / 2147483648.0;
                    else v = static_cast<int32_t>(le32(s)) / 2147483648.0;
                    sum += v;
                }
                mono[f] = static_cast<float>(sum / channels);
            }
            if (mono.empty()) { error = "no samples"; return false; }
            return true;
        }
        pos = body + size + (size & 1u);
    }
    error = "no audio data";
    return false;
}

/** Windowed-sinc resampling to [target] Hz; an IR is short, so quality wins over speed. */
std::vector<float> resample(const std::vector<float> &input, double from, double target) {
    if (std::fabs(from - target) < 0.5) return input;
    const double ratio = target / from;
    const double cutoff = std::min(1.0, ratio) * 0.95;
    constexpr int kHalf = 32;  // zero crossings per side
    const auto frames = static_cast<size_t>(std::ceil(input.size() * ratio));
    std::vector<float> output(frames);
    const double width = kHalf / cutoff;
    for (size_t n = 0; n < frames; ++n) {
        const double center = n / ratio;
        const auto first = static_cast<long>(std::ceil(center - width));
        const auto last = static_cast<long>(std::floor(center + width));
        double sum = 0.0;
        for (long i = std::max(0L, first); i <= std::min<long>(last, static_cast<long>(input.size()) - 1); ++i) {
            const double t = i - center;
            const double x = t * cutoff;
            const double sinc = std::fabs(x) < 1e-9 ? 1.0 : std::sin(M_PI * x) / (M_PI * x);
            const double w = 0.42 + 0.5 * std::cos(M_PI * t / width) + 0.08 * std::cos(2.0 * M_PI * t / width);
            sum += input[i] * cutoff * sinc * w;
        }
        output[n] = static_cast<float>(sum);
    }
    return output;
}

/** The IR at the project rate, cut to its maximum length and scaled to unit energy. */
bool loadImpulseResponse(const std::string &path, std::vector<float> &ir, double &originalRate, std::string &error) {
    std::vector<float> raw;
    if (!readWavAnyMono(path, raw, originalRate, error)) return false;
    if (originalRate < 8000 || originalRate > 384000) { error = "unsupported sample rate"; return false; }
    if (raw.size() > static_cast<size_t>(kMaxIrFrames * originalRate / kRate)) {
        raw.resize(static_cast<size_t>(kMaxIrFrames * originalRate / kRate));
    }
    ir = resample(raw, originalRate, kRate);
    // Trailing near-silence only costs convolution time.
    float peak = 0.0f;
    for (float v : ir) peak = std::max(peak, std::fabs(v));
    if (peak <= 0.0f) { error = "silent impulse response"; return false; }
    size_t end = ir.size();
    while (end > 1 && std::fabs(ir[end - 1]) < peak * 1e-5f) --end;
    ir.resize(end);
    double energy = 0.0;
    for (float v : ir) energy += static_cast<double>(v) * v;
    const auto scale = static_cast<float>(1.0 / std::sqrt(energy));
    for (float &v : ir) v *= scale;
    return true;
}

// -------------------------------------------------------------------------------------------------------- registry

struct ToneEntry {
    std::string path;
    ToneKind kind;
    std::shared_ptr<const nlohmann::json> model;   // amp models: the parsed profile
    std::shared_ptr<const std::vector<float>> ir;  // cabinets: the IR at the project rate
};

std::mutex gRegistryMutex;
std::vector<ToneEntry> gRegistry;

std::shared_ptr<const nlohmann::json> parseProfile(const std::string &path, std::string &error) {
    try {
        std::ifstream file(utf8Path(path));
        if (!file) { error = "cannot open file"; return nullptr; }
        auto json = std::make_shared<nlohmann::json>(nlohmann::json::parse(file));
        return json;
    } catch (const std::exception &e) {
        error = e.what();
        return nullptr;
    }
}

std::unique_ptr<nam::DSP> createModel(const nlohmann::json &profile, std::string &error) {
    try {
        nam::dspData data;
        auto model = nam::get_dsp(profile, data);
        if (!model) error = "unsupported profile";
        return model;
    } catch (const std::exception &e) {
        error = e.what();
        return nullptr;
    }
}

// --------------------------------------------------------------------------------------------------------- FFT

class Fft {
public:
    explicit Fft(int size) : mSize(size), mTwiddles(size / 2), mReverse(size) {
        for (int i = 0; i < size / 2; ++i) mTwiddles[i] = std::polar(1.0f, static_cast<float>(-2.0 * M_PI * i / size));
        int bits = 0;
        while ((1 << bits) < size) ++bits;
        for (int i = 0; i < size; ++i) {
            int r = 0;
            for (int b = 0; b < bits; ++b) if (i & (1 << b)) r |= 1 << (bits - 1 - b);
            mReverse[i] = r;
        }
    }

    void transform(Complex *a, bool inverse) const {
        for (int i = 0; i < mSize; ++i) if (i < mReverse[i]) std::swap(a[i], a[mReverse[i]]);
        for (int length = 2; length <= mSize; length <<= 1) {
            const int step = mSize / length;
            const int half = length / 2;
            for (int i = 0; i < mSize; i += length) {
                for (int j = 0; j < half; ++j) {
                    const Complex w = inverse ? std::conj(mTwiddles[j * step]) : mTwiddles[j * step];
                    const Complex u = a[i + j];
                    const Complex v = a[i + j + half] * w;
                    a[i + j] = u + v;
                    a[i + j + half] = u - v;
                }
            }
        }
    }

private:
    int mSize;
    std::vector<Complex> mTwiddles;
    std::vector<int> mReverse;
};

/**
 * Zero-latency convolution of one channel: the first partition of the IR runs as a direct FIR, the later ones
 * as a uniformly partitioned overlap-save convolution computed once per completed input partition.
 */
class Convolver {
public:
    Convolver(const std::vector<float> &ir, const Fft &fft) : mFft(fft) {
        constexpr int P = kPartition;
        mHead.assign(P, 0.0f);
        std::copy_n(ir.begin(), std::min<size_t>(P, ir.size()), mHead.begin());
        const int partitions = static_cast<int>((ir.size() + P - 1) / P);
        for (int k = 1; k < partitions; ++k) {
            std::vector<Complex> spectrum(2 * P);
            for (int i = 0; i < P && k * P + i < static_cast<int>(ir.size()); ++i) spectrum[i] = ir[k * P + i];
            mFft.transform(spectrum.data(), false);
            mTail.push_back(std::move(spectrum));
        }
        mSpectra.assign(std::max<size_t>(mTail.size(), 1), std::vector<Complex>(2 * P));
        mHistory.assign(2 * P, 0.0f);
        mPrevious.assign(P, 0.0f);
        mCurrent.assign(P, 0.0f);
        mTailOut.assign(P, 0.0f);
        mScratch.assign(2 * P, Complex{});
    }

    void process(float *samples, int frames) {
        constexpr int P = kPartition;
        for (int n = 0; n < frames; ++n) {
            const float x = samples[n];
            mHistory[mWrite] = x;
            mHistory[mWrite + P] = x;
            // x[n - i] = mHistory[mWrite + P - i]
            const float *h = mHead.data();
            const float *recent = &mHistory[mWrite + 1];
            float y = 0.0f;
            for (int i = 0; i < P; ++i) y += h[P - 1 - i] * recent[i];
            samples[n] = y + mTailOut[mPosition];
            mWrite = (mWrite + 1) % P;
            mCurrent[mPosition] = x;
            if (++mPosition == P) completePartition();
        }
    }

private:
    void completePartition() {
        constexpr int P = kPartition;
        mPosition = 0;
        if (mTail.empty()) return;
        // Spectrum of [previous, current]; slot mNewest holds the most recent block.
        mNewest = (mNewest + 1) % mSpectra.size();
        std::vector<Complex> &x = mSpectra[mNewest];
        for (int i = 0; i < P; ++i) {
            x[i] = mPrevious[i];
            x[P + i] = mCurrent[i];
        }
        mFft.transform(x.data(), false);
        std::swap(mPrevious, mCurrent);
        // Tail of the next output block: block (next - k) through partition k, k >= 1.
        std::fill(mScratch.begin(), mScratch.end(), Complex{});
        for (size_t k = 1; k <= mTail.size(); ++k) {
            const size_t age = k - 1;  // the newest block feeds partition 1 into the next output block
            if (age >= mSpectra.size()) break;
            const std::vector<Complex> &block = mSpectra[(mNewest + mSpectra.size() - age) % mSpectra.size()];
            const std::vector<Complex> &partition = mTail[k - 1];
            for (int i = 0; i < 2 * P; ++i) mScratch[i] += block[i] * partition[i];
        }
        mFft.transform(mScratch.data(), true);
        const float scale = 1.0f / (2 * P);
        for (int i = 0; i < P; ++i) mTailOut[i] = mScratch[P + i].real() * scale;
    }

    const Fft &mFft;
    std::vector<float> mHead;
    std::vector<std::vector<Complex>> mTail;     // partitions 1.. as spectra
    std::vector<std::vector<Complex>> mSpectra;  // input block spectra, ring
    std::vector<float> mHistory, mPrevious, mCurrent, mTailOut;
    std::vector<Complex> mScratch;
    size_t mNewest = 0;
    int mWrite = 0;
    int mPosition = 0;
};

class CabinetProcessor : public ToneProcessor {
public:
    CabinetProcessor(const std::vector<float> &ir, bool stereo)
        : mFft(2 * kPartition), mFrames(static_cast<int>(ir.size())), mLeft(ir, mFft) {
        if (stereo) mRight = std::make_unique<Convolver>(ir, mFft);
    }

    void process(float *left, float *right, int frames) override {
        mLeft.process(left, frames);
        if (mRight) mRight->process(right, frames);
    }

    int tailFrames() const override { return mFrames; }

private:
    Fft mFft;
    int mFrames;
    Convolver mLeft;
    std::unique_ptr<Convolver> mRight;
};

class AmpProcessor : public ToneProcessor {
public:
    AmpProcessor(std::unique_ptr<nam::DSP> left, std::unique_ptr<nam::DSP> right, float inputDb, float outputDb,
                 int sampleRate)
        : mLeft(std::move(left)), mRight(std::move(right)),
          mInputGain(static_cast<float>(std::pow(10.0, inputDb / 20.0))),
          mOutputGain(static_cast<float>(std::pow(10.0, outputDb / 20.0))), mIn(kAmpBlock), mOut(kAmpBlock) {
        mLeft->Reset(sampleRate, kAmpBlock);
        if (mRight) mRight->Reset(sampleRate, kAmpBlock);
    }

    void process(float *left, float *right, int frames) override {
        run(*mLeft, left, frames);
        if (mRight) run(*mRight, right, frames);
    }

    int tailFrames() const override { return 0; }

private:
    void run(nam::DSP &model, float *samples, int frames) {
        for (int start = 0; start < frames; start += kAmpBlock) {
            const int count = std::min(kAmpBlock, frames - start);
            for (int i = 0; i < count; ++i) mIn[i] = samples[start + i] * mInputGain;
            float *in = mIn.data();
            float *out = mOut.data();
            model.process(&in, &out, count);
            for (int i = 0; i < count; ++i) samples[start + i] = mOut[i] * mOutputGain;
        }
    }

    std::unique_ptr<nam::DSP> mLeft, mRight;
    float mInputGain, mOutputGain;
    std::vector<float> mIn, mOut;
};

}  // namespace

ToneFileInfo inspectToneFile(const std::string &path, ToneKind kind) {
    ToneFileInfo info;
    if (kind == ToneKind::Cabinet) {
        std::vector<float> ir;
        if (!loadImpulseResponse(path, ir, info.sampleRate, info.error)) return info;
        info.frames = static_cast<int>(ir.size());
        info.ok = true;
        return info;
    }
    auto profile = parseProfile(path, info.error);
    if (!profile) return info;
    auto model = createModel(*profile, info.error);
    if (!model) return info;
    info.sampleRate = model->GetExpectedSampleRate();
    if (info.sampleRate > 0 && std::fabs(info.sampleRate - kRate) > 0.5) {
        info.error = "profile is not 48 kHz";
        return info;
    }
    if (model->NumInputChannels() != 1 || model->NumOutputChannels() != 1) {
        info.error = "profile is not mono";
        return info;
    }
    if (profile->contains("metadata") && (*profile)["metadata"].is_object()) info.metadata = (*profile)["metadata"].dump();
    info.ok = true;
    return info;
}

int loadTone(const std::string &path, ToneKind kind) {
    {
        std::lock_guard<std::mutex> lock(gRegistryMutex);
        for (size_t i = 0; i < gRegistry.size(); ++i) {
            if (gRegistry[i].path == path && gRegistry[i].kind == kind) return static_cast<int>(i);
        }
    }
    ToneEntry entry{path, kind, nullptr, nullptr};
    std::string error;
    if (kind == ToneKind::Cabinet) {
        auto ir = std::make_shared<std::vector<float>>();
        double rate = 0;
        if (!loadImpulseResponse(path, *ir, rate, error)) {
            MYAUDIO_LOGW(kTag, "Cannot load impulse response %s: %s", path.c_str(), error.c_str());
            return -1;
        }
        entry.ir = std::move(ir);
    } else {
        entry.model = parseProfile(path, error);
        if (!entry.model || !createModel(*entry.model, error)) {
            MYAUDIO_LOGW(kTag, "Cannot load amp model %s: %s", path.c_str(), error.c_str());
            return -1;
        }
    }
    std::lock_guard<std::mutex> lock(gRegistryMutex);
    gRegistry.push_back(std::move(entry));
    return static_cast<int>(gRegistry.size() - 1);
}

std::unique_ptr<ToneProcessor> ToneProcessor::create(ToneKind kind, int handle, float inputDb, float outputDb,
                                                     bool stereo, int sampleRate) {
    ToneEntry entry;
    {
        std::lock_guard<std::mutex> lock(gRegistryMutex);
        if (handle < 0 || handle >= static_cast<int>(gRegistry.size()) || gRegistry[handle].kind != kind) return nullptr;
        entry = gRegistry[handle];
    }
    if (kind == ToneKind::Cabinet) return std::make_unique<CabinetProcessor>(*entry.ir, stereo);
    std::string error;
    auto left = createModel(*entry.model, error);
    std::unique_ptr<nam::DSP> right;
    if (left && stereo) right = createModel(*entry.model, error);
    if (!left || (stereo && !right)) {
        MYAUDIO_LOGW(kTag, "Cannot create amp model %d: %s", handle, error.c_str());
        return nullptr;
    }
    return std::make_unique<AmpProcessor>(std::move(left), std::move(right), inputDb, outputDb, sampleRate);
}

}  // namespace myaudio
