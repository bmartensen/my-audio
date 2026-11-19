// Opus encoding of the shared mix on the desktop (Android uses MediaCodec). Returns the raw Opus
// packets; the Ogg container is written by the shared Kotlin OggOpusWriter, as on Android.
#include <jni.h>

#include <algorithm>
#include <vector>

#include <opus.h>

#include "Log.h"

namespace {

constexpr int kSampleRate = 48000;
constexpr int kFrameSamples = 960;      // 20 ms per packet
constexpr int kMaxPacketBytes = 4000;   // libopus recommendation for one packet

}  // namespace

/**
 * Encodes interleaved float [samples] (48 kHz, [channels]) at [bitrate] bit/s. [preSkip] receives
 * the encoder delay in samples (OpusHead pre-skip). Returns one byte array per packet, or null.
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_encodeOpus(JNIEnv *env, jobject /* this */, jfloatArray samples,
                                                                  jint channels, jint bitrate, jintArray preSkip) {
    int error = OPUS_OK;
    OpusEncoder *encoder = opus_encoder_create(kSampleRate, channels, OPUS_APPLICATION_AUDIO, &error);
    if (error != OPUS_OK || encoder == nullptr) {
        MYAUDIO_LOGW("MyAudioExport", "Cannot create the Opus encoder (%d)", error);
        return nullptr;
    }
    opus_encoder_ctl(encoder, OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(10));
    opus_int32 lookahead = 0;
    opus_encoder_ctl(encoder, OPUS_GET_LOOKAHEAD(&lookahead));
    env->SetIntArrayRegion(preSkip, 0, 1, &lookahead);

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> input(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, input.data());
    const size_t totalFrames = input.size() / static_cast<size_t>(channels);

    std::vector<std::vector<unsigned char>> packets;
    std::vector<float> frame(static_cast<size_t>(kFrameSamples * channels));
    std::vector<unsigned char> packet(kMaxPacketBytes);
    // The last partial frame is padded with silence; OggOpusWriter trims the end via the granule.
    for (size_t start = 0; start < totalFrames; start += kFrameSamples) {
        const size_t frames = std::min<size_t>(kFrameSamples, totalFrames - start);
        std::fill(frame.begin(), frame.end(), 0.0f);
        std::copy_n(input.begin() + static_cast<std::ptrdiff_t>(start * channels), frames * channels, frame.begin());
        const opus_int32 bytes = opus_encode_float(encoder, frame.data(), kFrameSamples, packet.data(), kMaxPacketBytes);
        if (bytes < 0) {
            MYAUDIO_LOGW("MyAudioExport", "Opus encoding failed (%d)", bytes);
            opus_encoder_destroy(encoder);
            return nullptr;
        }
        packets.emplace_back(packet.begin(), packet.begin() + bytes);
    }
    opus_encoder_destroy(encoder);

    jobjectArray result = env->NewObjectArray(static_cast<jsize>(packets.size()), env->FindClass("[B"), nullptr);
    for (size_t i = 0; i < packets.size(); ++i) {
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(packets[i].size()));
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(packets[i].size()),
                                reinterpret_cast<const jbyte *>(packets[i].data()));
        env->SetObjectArrayElement(result, static_cast<jsize>(i), bytes);
        env->DeleteLocalRef(bytes);
    }
    return result;
}
