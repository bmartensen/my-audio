#include "Wav.h"

#include <cstring>

#if defined(_WIN32)
#include <windows.h>
#endif

namespace myaudio {
namespace {

/** Opens a UTF-8 path; Windows needs the wide-character API for paths outside the ANSI code page. */
FILE *openFile(const std::string &path, const wchar_t *wideMode, const char *mode) {
#if defined(_WIN32)
    const int length = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, nullptr, 0);
    if (length <= 0) return nullptr;
    std::wstring wide(static_cast<size_t>(length), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, wide.data(), length);
    return _wfopen(wide.c_str(), wideMode);
#else
    (void) wideMode;
    return std::fopen(path.c_str(), mode);
#endif
}

}  // namespace

namespace {

constexpr int kHeaderSize = 44;

void putU32(uint8_t *target, uint32_t value) {
    for (int i = 0; i < 4; ++i) target[i] = static_cast<uint8_t>(value >> (8 * i));
}

void putU16(uint8_t *target, uint16_t value) {
    target[0] = static_cast<uint8_t>(value);
    target[1] = static_cast<uint8_t>(value >> 8);
}

uint32_t getU32(const uint8_t *source) {
    return source[0] | (source[1] << 8) | (source[2] << 16) | (static_cast<uint32_t>(source[3]) << 24);
}

uint16_t getU16(const uint8_t *source) {
    return static_cast<uint16_t>(source[0] | (source[1] << 8));
}

void fillHeader(uint8_t *header, int sampleRate, uint32_t dataBytes, int channels) {
    std::memcpy(header, "RIFF", 4);
    putU32(header + 4, 36 + dataBytes);
    std::memcpy(header + 8, "WAVEfmt ", 8);
    putU32(header + 16, 16);              // fmt chunk size
    putU16(header + 20, 1);               // PCM
    putU16(header + 22, channels);
    putU32(header + 24, sampleRate);
    putU32(header + 28, sampleRate * 2 * channels);  // byte rate
    putU16(header + 32, 2 * channels);               // block align
    putU16(header + 34, 16);              // bits per sample
    std::memcpy(header + 36, "data", 4);
    putU32(header + 40, dataBytes);
}

}  // namespace

bool readWavMono16(const std::string &path, std::vector<int16_t> &samples, int &sampleRate) {
    int channels = 0;
    return readWav16(path, samples, sampleRate, channels) && channels == 1;
}

bool readWav16(const std::string &path, std::vector<int16_t> &samples, int &sampleRate, int &channels) {
    samples.clear();
    FILE *file = openFile(path, L"rb", "rb");
    if (file == nullptr) return false;
    uint8_t riff[12];
    bool ok = std::fread(riff, 1, sizeof(riff), file) == sizeof(riff)
              && std::memcmp(riff, "RIFF", 4) == 0 && std::memcmp(riff + 8, "WAVE", 4) == 0;
    bool formatOk = false;
    while (ok) {
        uint8_t chunk[8];
        if (std::fread(chunk, 1, sizeof(chunk), file) != sizeof(chunk)) {
            ok = false;
            break;
        }
        const uint32_t size = getU32(chunk + 4);
        if (std::memcmp(chunk, "fmt ", 4) == 0) {
            uint8_t format[16];
            if (size < sizeof(format) || std::fread(format, 1, sizeof(format), file) != sizeof(format)) {
                ok = false;
                break;
            }
            channels = getU16(format + 2);
            formatOk = getU16(format) == 1 && (channels == 1 || channels == 2)
                       && getU16(format + 12) == channels * 2 && getU16(format + 14) == 16;
            sampleRate = static_cast<int>(getU32(format + 4));
            std::fseek(file, static_cast<long>(size - sizeof(format) + (size & 1)), SEEK_CUR);
        } else if (std::memcmp(chunk, "data", 4) == 0) {
            if (!formatOk) {
                ok = false;
                break;
            }
            samples.resize(size / 2);
            // A recording interrupted before finish() may be shorter than its header claims.
            samples.resize(std::fread(samples.data(), 2, samples.size(), file));
            samples.resize(samples.size() / channels * channels);
            break;
        } else {
            std::fseek(file, static_cast<long>(size + (size & 1)), SEEK_CUR);
        }
    }
    std::fclose(file);
    return ok && formatOk;
}

WavWriter::~WavWriter() {
    finish();
}

bool WavWriter::open(const std::string &path, int sampleRate, int channels) {
    if (channels < 1 || channels > 2) return false;
    mFile = openFile(path, L"wb", "wb");
    if (mFile == nullptr) return false;
    mFrames = 0;
    mChannels = channels;
    uint8_t header[kHeaderSize];
    fillHeader(header, sampleRate, 0, channels);
    // Keep the sample rate for finish() by writing it now; only the sizes change later.
    return std::fwrite(header, 1, sizeof(header), mFile) == sizeof(header);
}

bool WavWriter::write(const int16_t *samples, size_t count) {
    if (mFile == nullptr) return false;
    const size_t written = std::fwrite(samples, sizeof(int16_t) * mChannels, count, mFile);
    mFrames += static_cast<int64_t>(written);
    return written == count;
}

int64_t WavWriter::finish() {
    if (mFile == nullptr) return mFrames;
    const uint32_t dataBytes = static_cast<uint32_t>(mFrames * 2 * mChannels);
    uint8_t sizes[4];
    putU32(sizes, 36 + dataBytes);
    std::fseek(mFile, 4, SEEK_SET);
    std::fwrite(sizes, 1, 4, mFile);
    putU32(sizes, dataBytes);
    std::fseek(mFile, 40, SEEK_SET);
    std::fwrite(sizes, 1, 4, mFile);
    std::fclose(mFile);
    mFile = nullptr;
    return mFrames;
}

}  // namespace myaudio
