#include "DesktopDevices.h"

#include <jni.h>

#include <algorithm>
#include <mutex>

#include "Log.h"

namespace myaudio {
namespace {

std::mutex gLock;
ma_context gContext;
bool gContextReady = false;
std::string gPlayback;
std::string gCapture;

struct Found {
    std::string name;
    ma_device_id id{};
    bool found = false;
};

/** Looks [name] up among the current devices; call with gLock held. */
Found find(bool capture, const std::string &name) {
    Found result;
    if (name.empty() || !gContextReady) return result;
    ma_device_info *playbackInfos = nullptr;
    ma_device_info *captureInfos = nullptr;
    ma_uint32 playbackCount = 0;
    ma_uint32 captureCount = 0;
    if (ma_context_get_devices(&gContext, &playbackInfos, &playbackCount, &captureInfos, &captureCount) != MA_SUCCESS) {
        return result;
    }
    const ma_device_info *infos = capture ? captureInfos : playbackInfos;
    const ma_uint32 count = capture ? captureCount : playbackCount;
    for (ma_uint32 i = 0; i < count; ++i) {
        if (name == infos[i].name) {
            result.name = name;
            result.id = infos[i].id;
            result.found = true;
            break;
        }
    }
    return result;
}

std::string toString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

ma_context *audioContext() {
    std::lock_guard<std::mutex> guard(gLock);
    if (!gContextReady) gContextReady = ma_context_init(nullptr, 0, nullptr, &gContext) == MA_SUCCESS;
    return gContextReady ? &gContext : nullptr;
}

std::vector<std::string> deviceNames(bool capture) {
    audioContext();
    std::lock_guard<std::mutex> guard(gLock);
    std::vector<std::string> names;
    if (!gContextReady) return names;
    ma_device_info *playbackInfos = nullptr;
    ma_device_info *captureInfos = nullptr;
    ma_uint32 playbackCount = 0;
    ma_uint32 captureCount = 0;
    if (ma_context_get_devices(&gContext, &playbackInfos, &playbackCount, &captureInfos, &captureCount) != MA_SUCCESS) {
        return names;
    }
    const ma_device_info *infos = capture ? captureInfos : playbackInfos;
    const ma_uint32 count = capture ? captureCount : playbackCount;
    for (ma_uint32 i = 0; i < count; ++i) names.emplace_back(infos[i].name);
    return names;
}

void selectDevices(const std::string &playback, const std::string &capture) {
    std::lock_guard<std::mutex> guard(gLock);
    gPlayback = playback;
    gCapture = capture;
}

void applySelectedDevices(ma_device_config &config, ma_device_id &playbackId, ma_device_id &captureId,
                          std::string &usedPlayback, std::string &usedCapture) {
    audioContext();
    std::lock_guard<std::mutex> guard(gLock);
    const Found playback = find(false, gPlayback);
    const Found capture = find(true, gCapture);
    usedPlayback = playback.name;
    usedCapture = capture.name;
    if (playback.found) {
        playbackId = playback.id;
        config.playback.pDeviceID = &playbackId;
    } else if (!gPlayback.empty()) {
        MYAUDIO_LOGW("MyAudioEngine", "Playback device %s not connected, using the default", gPlayback.c_str());
    }
    if (capture.found) {
        captureId = capture.id;
        config.capture.pDeviceID = &captureId;
    } else if (!gCapture.empty()) {
        MYAUDIO_LOGW("MyAudioEngine", "Capture device %s not connected, using the default", gCapture.c_str());
    }
}

int captureChannels() {
    ma_context *context = audioContext();
    if (context == nullptr) return 1;
    ma_device_id id{};
    bool selected = false;
    {
        std::lock_guard<std::mutex> guard(gLock);
        const Found capture = find(true, gCapture);
        if (capture.found) {
            id = capture.id;
            selected = true;
        }
    }
    ma_device_info info;
    if (ma_context_get_device_info(context, ma_device_type_capture, selected ? &id : nullptr, &info) != MA_SUCCESS) return 1;
    ma_uint32 channels = 0;
    for (ma_uint32 i = 0; i < info.nativeDataFormatCount; ++i) channels = std::max(channels, info.nativeDataFormats[i].channels);
    return channels > 0 ? static_cast<int>(channels) : 1;
}

bool initDevice(ma_device_config &config, ma_device *device, const char *tag) {
    ma_device_id playbackId;
    ma_device_id captureId;
    std::string playbackName;
    std::string captureName;
    applySelectedDevices(config, playbackId, captureId, playbackName, captureName);
    ma_context *context = audioContext();
    if (config.deviceType == ma_device_type_duplex || config.deviceType == ma_device_type_capture) {
        config.capture.shareMode = ma_share_mode_exclusive;
        if (ma_device_init(context, &config, device) == MA_SUCCESS) {
            MYAUDIO_LOGI(tag, "Microphone in exclusive mode");
            return true;
        }
        MYAUDIO_LOGW(tag, "Exclusive microphone not available, using shared mode");
        config.capture.shareMode = ma_share_mode_shared;
    }
    return ma_device_init(context, &config, device) == MA_SUCCESS;
}

}  // namespace myaudio

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_deviceNames(JNIEnv *env, jobject /* this */, jboolean capture) {
    const std::vector<std::string> names = myaudio::deviceNames(capture);
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(names.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < names.size(); ++i) {
        jstring name = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(array, static_cast<jsize>(i), name);
        env->DeleteLocalRef(name);
    }
    return array;
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_selectDevices(JNIEnv *env, jobject /* this */, jstring playback,
                                                                     jstring capture) {
    myaudio::selectDevices(myaudio::toString(env, playback), myaudio::toString(env, capture));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_inputChannels(JNIEnv * /* env */, jobject /* this */) {
    return myaudio::captureChannels();
}
