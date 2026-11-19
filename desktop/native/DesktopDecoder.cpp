// Audio file decoding for the desktop app (Android uses MediaCodec instead). Returns the decoded
// interleaved mono/stereo samples at the file's own rate; resampling and the WAV take are done by the
// shared Kotlin code (ImportedAudioWriter), exactly as on Android.
#include <jni.h>

#if defined(_WIN32)
#include <windows.h>
#endif

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "Log.h"
#include "miniaudio.h"
#include <opus.h>

namespace {

std::string toString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

/** Reads a whole file given as UTF-8 path; empty on failure. */
std::vector<unsigned char> readFile(const std::string &path) {
#if defined(_WIN32)
    const int length = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, nullptr, 0);
    std::wstring wide(static_cast<size_t>(length > 0 ? length : 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, wide.data(), length);
    FILE *file = _wfopen(wide.c_str(), L"rb");
#else
    FILE *file = std::fopen(path.c_str(), "rb");
#endif
    std::vector<unsigned char> data;
    if (file == nullptr) return data;
    unsigned char buffer[65536];
    size_t read = 0;
    while ((read = std::fread(buffer, 1, sizeof(buffer), file)) > 0) data.insert(data.end(), buffer, buffer + read);
    std::fclose(file);
    return data;
}

uint16_t le16(const unsigned char *p) { return static_cast<uint16_t>(p[0] | (p[1] << 8)); }

int64_t le64(const unsigned char *p) {
    uint64_t value = 0;
    for (int i = 7; i >= 0; --i) value = (value << 8) | p[i];
    return static_cast<int64_t>(value);
}

/**
 * Ogg Opus (RFC 7845), which miniaudio cannot read: the packets of the first logical stream are
 * reassembled from the pages, decoded at 48 kHz, the pre-skip dropped and the end trimmed to the
 * last granule position. Returns false if [data] is not Ogg Opus; [samples] receives interleaved PCM.
 */
bool decodeOggOpus(const std::vector<unsigned char> &data, std::vector<float> &samples, int &channels) {
    if (data.size() < 27 || std::memcmp(data.data(), "OggS", 4) != 0) return false;
    std::vector<std::vector<unsigned char>> packets;
    std::vector<unsigned char> current;
    int64_t lastGranule = -1;
    uint32_t serial = 0;
    bool serialKnown = false;
    size_t pos = 0;
    while (pos + 27 <= data.size() && std::memcmp(data.data() + pos, "OggS", 4) == 0) {
        const unsigned char *header = data.data() + pos;
        const int64_t granule = le64(header + 6);
        const uint32_t pageSerial = static_cast<uint32_t>(header[14] | (header[15] << 8) | (header[16] << 16) | (header[17] << 24));
        const int segments = header[26];
        if (pos + 27 + segments > data.size()) break;
        const unsigned char *lacing = header + 27;
        size_t body = pos + 27 + segments;
        if (!serialKnown) {
            serial = pageSerial;
            serialKnown = true;
        }
        const bool ours = pageSerial == serial;
        for (int s = 0; s < segments; ++s) {
            const size_t size = lacing[s];
            if (body + size > data.size()) return false;
            if (ours) {
                current.insert(current.end(), data.begin() + static_cast<std::ptrdiff_t>(body),
                               data.begin() + static_cast<std::ptrdiff_t>(body + size));
                if (size < 255) {
                    packets.push_back(std::move(current));
                    current.clear();
                }
            }
            body += size;
        }
        if (ours && granule >= 0) lastGranule = granule;
        pos = body;
    }
    if (packets.size() < 3 || packets[0].size() < 19 || std::memcmp(packets[0].data(), "OpusHead", 8) != 0) return false;
    channels = packets[0][9];
    const int preSkip = le16(packets[0].data() + 10);
    const int16_t gainQ8 = static_cast<int16_t>(le16(packets[0].data() + 16));
    if (channels < 1 || channels > 2 || packets[0][18] != 0) return false;  // mapping family 0 only

    int error = OPUS_OK;
    OpusDecoder *decoder = opus_decoder_create(48000, channels, &error);
    if (error != OPUS_OK || decoder == nullptr) return false;
    opus_decoder_ctl(decoder, OPUS_SET_GAIN(gainQ8));
    std::vector<float> pcm(5760 * static_cast<size_t>(channels));  // 120 ms, the longest Opus packet
    std::vector<float> decoded;
    for (size_t i = 2; i < packets.size(); ++i) {  // 0: OpusHead, 1: OpusTags
        const int frames = opus_decode_float(decoder, packets[i].data(), static_cast<opus_int32>(packets[i].size()),
                                             pcm.data(), 5760, 0);
        if (frames < 0) {
            opus_decoder_destroy(decoder);
            return false;
        }
        decoded.insert(decoded.end(), pcm.begin(), pcm.begin() + frames * channels);
    }
    opus_decoder_destroy(decoder);
    // Granule positions count from the start of the stream, pre-skip included.
    const int64_t end = lastGranule > preSkip ? std::min<int64_t>(lastGranule, static_cast<int64_t>(decoded.size() / channels))
                                              : static_cast<int64_t>(decoded.size() / channels);
    if (end <= preSkip) return false;
    samples.assign(decoded.begin() + preSkip * channels, decoded.begin() + static_cast<std::ptrdiff_t>(end * channels));
    return true;
}

}  // namespace

/** Interleaved mono/stereo samples of [path], or null if it is no readable audio file; [info] receives rate and channels. */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_decodeAudio(JNIEnv *env, jobject /* this */, jstring path,
                                                                  jintArray info) {
    const std::string file = toString(env, path);
    {
        // Ogg Opus first: miniaudio reads Ogg Vorbis, but not Opus.
        std::vector<float> opus;
        int channels = 0;
        if (decodeOggOpus(readFile(file), opus, channels)) {
            const jint values[] = {48000, channels};
            env->SetIntArrayRegion(info, 0, 2, values);
            jfloatArray array = env->NewFloatArray(static_cast<jsize>(opus.size()));
            if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(opus.size()), opus.data());
            return array;
        }
    }
    // Native rate and channel count; float samples.
    ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, 0);
    ma_decoder decoder;
#if defined(_WIN32)
    const int length = MultiByteToWideChar(CP_UTF8, 0, file.c_str(), -1, nullptr, 0);
    std::wstring wide(static_cast<size_t>(length > 0 ? length : 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, file.c_str(), -1, wide.data(), length);
    const ma_result opened = ma_decoder_init_file_w(wide.c_str(), &config, &decoder);
#else
    const ma_result opened = ma_decoder_init_file(file.c_str(), &config, &decoder);
#endif
    if (opened != MA_SUCCESS) {
        MYAUDIO_LOGW("MyAudioImport", "Cannot decode %s (%d)", file.c_str(), opened);
        return nullptr;
    }
    const ma_uint32 channels = decoder.outputChannels;
    const ma_uint32 sampleRate = decoder.outputSampleRate;
    if (channels < 1 || channels > 2) {
        ma_decoder_uninit(&decoder);
        return nullptr;
    }
    std::vector<float> samples;
    std::vector<float> block(4096 * static_cast<size_t>(channels));
    while (true) {
        ma_uint64 read = 0;
        const ma_result result = ma_decoder_read_pcm_frames(&decoder, block.data(), 4096, &read);
        samples.insert(samples.end(), block.begin(), block.begin() + static_cast<std::ptrdiff_t>(read * channels));
        if (result != MA_SUCCESS || read == 0) break;
    }
    ma_decoder_uninit(&decoder);
    if (samples.empty() || sampleRate == 0) return nullptr;

    const jint values[] = {static_cast<jint>(sampleRate), static_cast<jint>(channels)};
    env->SetIntArrayRegion(info, 0, 2, values);
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(samples.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(samples.size()), samples.data());
    return array;
}
