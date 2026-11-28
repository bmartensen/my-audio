// Microphone input for the guitar tuner on the desktop (Android uses AudioRecord). Delivers mono
// samples at the requested rate through a lock-free queue; the shared Kotlin PitchDetector analyses them.
// A two-channel interface is opened in stereo and the tuner hears the channel the takes record.
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>

#include "DesktopDevices.h"
#include "DesktopEngine.h"
#include "Log.h"
#include "miniaudio.h"

namespace {

constexpr const char *kTag = "MyAudioTuner";

std::mutex gLock;
ma_device *gDevice = nullptr;
std::unique_ptr<myaudio::SpscFifo> gFifo;
std::atomic<bool> gLost{false};
myaudio::InputChannel gInputChannel = myaudio::InputChannel::Device;

void dataCallback(ma_device * /* device */, void * /* output */, const void *input, ma_uint32 frames) {
    if (!gFifo) return;
    const auto *samples = static_cast<const float *>(input);
    if (gInputChannel == myaudio::InputChannel::Device) {
        gFifo->write(samples, frames);  // a full queue drops the block
        return;
    }
    float mono[1024];
    for (ma_uint32 start = 0; start < frames; start += 1024) {
        const ma_uint32 count = std::min<ma_uint32>(1024, frames - start);
        for (ma_uint32 i = 0; i < count; ++i) mono[i] = myaudio::monoInputSample(samples + (start + i) * 2, 2, gInputChannel);
        gFifo->write(mono, count);
    }
}

void notificationCallback(const ma_device_notification *notification) {
    if (notification->type == ma_device_notification_type_rerouted ||
        notification->type == ma_device_notification_type_stopped) {
        gLost.store(true);
    }
}

void closeLocked() {
    if (gDevice == nullptr) return;
    ma_device_uninit(gDevice);
    delete gDevice;
    gDevice = nullptr;
    gFifo.reset();
}

}  // namespace

/**
 * Opens the selected microphone at [sampleRate]; [inputChannel] as for a take (InputChannel code, -1 = mono).
 * Returns its name, or null if it cannot be opened.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_startCapture(JNIEnv *env, jobject /* this */, jint sampleRate,
                                                                    jint inputChannel) {
    std::lock_guard<std::mutex> guard(gLock);
    closeLocked();
    gInputChannel = myaudio::toInputChannel(inputChannel);
    gFifo = std::make_unique<myaudio::SpscFifo>(static_cast<size_t>(sampleRate) * 2);
    gLost = false;
    auto *device = new ma_device;
    ma_device_config config = ma_device_config_init(ma_device_type_capture);
    config.sampleRate = static_cast<ma_uint32>(sampleRate);
    config.capture.format = ma_format_f32;
    config.capture.channels = gInputChannel == myaudio::InputChannel::Device ? 1 : 2;
    config.dataCallback = dataCallback;
    config.notificationCallback = notificationCallback;
    config.wasapi.noAutoStreamRouting = MA_TRUE;
    // The tuner only looks below about 1.2 kHz; a steep low-pass keeps the downsampling clean.
    config.resampling.linear.lpfOrder = MA_MAX_FILTER_ORDER;
    if (!myaudio::initDevice(config, device, kTag) || ma_device_start(device) != MA_SUCCESS) {
        delete device;
        gFifo.reset();
        return nullptr;
    }
    gDevice = device;
    return env->NewStringUTF(device->capture.name);
}

/** Copies up to buffer.size waiting samples into [buffer]; -1 once the device was lost. Never blocks. */
extern "C" JNIEXPORT jint JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_readCapture(JNIEnv *env, jobject /* this */, jfloatArray buffer) {
    if (gLost.load()) return -1;
    float samples[4096];
    const jsize capacity = std::min<jsize>(env->GetArrayLength(buffer), 4096);
    myaudio::SpscFifo *fifo = gFifo.get();
    if (fifo == nullptr) return -1;
    const auto count = static_cast<jint>(fifo->read(samples, static_cast<size_t>(capacity)));
    if (count > 0) env->SetFloatArrayRegion(buffer, 0, count, samples);
    return count;
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_desktop_DesktopNative_stopCapture(JNIEnv * /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> guard(gLock);
    closeLocked();
}
