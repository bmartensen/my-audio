#include <jni.h>

#include <string>
#include <vector>

#include "AirwindowsEffects.h"
#include "AmpCabinet.h"
#include "CalibrationSession.h"
#include "EffectPreview.h"
#include "LiveChain.h"
#include "Log.h"
#include "TransportEngine.h"

namespace {

std::string toString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

myaudio::TransportEngine &engine() {
    return myaudio::TransportEngine::instance();
}

/** An effect chain encoded by AudioEngine.encodeChain. */
std::vector<myaudio::EffectSlot> readChain(JNIEnv *env, jfloatArray encoded) {
    const jsize count = env->GetArrayLength(encoded);
    std::vector<jfloat> values(count);
    env->GetFloatArrayRegion(encoded, 0, count, values.data());
    return myaudio::decodeEffectChain(values.data(), values.size());
}

/** Track sources from the arrays of AudioEngine.TrackArguments; see there for the layout. */
std::vector<myaudio::TrackSource> readSources(JNIEnv *env, jobjectArray paths, jlongArray frames, jfloatArray parameters,
                                              jobjectArray chains, jobjectArray envelopes, jobjectArray envelopeGains) {
    // Per track: three paths (take, reference, cache), two longs (offset, count-in), five floats
    // and an encoded effect chain; layout must match AudioEngine.TrackArguments.
    constexpr jsize kPaths = 3;
    constexpr jsize kStride = 5;
    const jsize count = env->GetArrayLength(paths) / kPaths;
    std::vector<jlong> frameValues(count * 2);
    std::vector<jfloat> values(count * kStride);
    env->GetLongArrayRegion(frames, 0, count * 2, frameValues.data());
    env->GetFloatArrayRegion(parameters, 0, count * kStride, values.data());
    auto pathAt = [&](jsize index) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(paths, index));
        std::string result = toString(env, value);
        env->DeleteLocalRef(value);
        return result;
    };
    std::vector<myaudio::TrackSource> sources;
    for (jsize i = 0; i < count; ++i) {
        const jfloat *v = values.data() + i * kStride;
        sources.push_back({pathAt(i * kPaths), pathAt(i * kPaths + 1), pathAt(i * kPaths + 2), frameValues[i * 2],
                           frameValues[i * 2 + 1], v[0], v[1], v[2], v[3], v[4], {}, {}});
        auto chain = static_cast<jfloatArray>(env->GetObjectArrayElement(chains, i));
        sources.back().effects = readChain(env, chain);
        env->DeleteLocalRef(chain);
        auto pointFrames = static_cast<jlongArray>(env->GetObjectArrayElement(envelopes, i));
        auto pointGains = static_cast<jfloatArray>(env->GetObjectArrayElement(envelopeGains, i));
        const jsize n = env->GetArrayLength(pointFrames);
        std::vector<jlong> positions(n);
        std::vector<jfloat> gains(n);
        env->GetLongArrayRegion(pointFrames, 0, n, positions.data());
        env->GetFloatArrayRegion(pointGains, 0, n, gains.data());
        for (jsize p = 0; p < n; ++p) sources.back().volumeEnvelope.push_back({positions[p], gains[p]});
        env->DeleteLocalRef(pointFrames);
        env->DeleteLocalRef(pointGains);
    }
    return sources;
}

}  // namespace

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_runCalibrationSession(JNIEnv *env, jobject /* this */,
        jint outputDeviceId, jint inputDeviceId, jboolean bluetooth, jboolean handsFreeInput, jint inputChannel) {
    const myaudio::SessionResult result = myaudio::runCalibrationSession(outputDeviceId, inputDeviceId, bluetooth, handsFreeInput,
                                                                         myaudio::toInputChannel(inputChannel));
    // Layout must match SessionMeasurement.fromNative.
    const jdouble values[] = {
            static_cast<jdouble>(result.status),
            static_cast<jdouble>(result.sampleRate),
            static_cast<jdouble>(result.validChirps),
            result.latencyFrames,
            result.chirpSpreadFrames,
            result.minSnrDb,
            result.timestampLatencyFrames,
            static_cast<jdouble>(result.xruns),
            result.outputMMap ? 1.0 : 0.0,
            result.inputMMap ? 1.0 : 0.0,
            static_cast<jdouble>(result.framesPerBurst),
            static_cast<jdouble>(result.outputBufferFrames),
    };
    const jsize count = sizeof(values) / sizeof(values[0]);
    jdoubleArray array = env->NewDoubleArray(count);
    if (array != nullptr) env->SetDoubleArrayRegion(array, 0, count, values);
    return array;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_setTracks(JNIEnv *env, jobject /* this */, jobjectArray paths,
                                                          jlongArray frames, jfloatArray parameters, jobjectArray chains,
                                                          jobjectArray envelopes, jobjectArray envelopeGains,
                                                          jfloatArray master) {
    return engine().setTracks(readSources(env, paths, frames, parameters, chains, envelopes, envelopeGains),
                              readChain(env, master));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_exportStems(JNIEnv *env, jobject /* this */, jobjectArray paths,
                                                            jlongArray frames, jfloatArray parameters, jobjectArray chains,
                                                            jobjectArray envelopes, jobjectArray envelopeGains,
                                                            jlong projectFrames, jobjectArray targets) {
    std::vector<std::string> files;
    for (jsize i = 0; i < env->GetArrayLength(targets); ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(targets, i));
        files.push_back(toString(env, value));
        env->DeleteLocalRef(value);
    }
    return engine().exportStems(readSources(env, paths, frames, parameters, chains, envelopes, envelopeGains), projectFrames,
                                files);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_startPlayback(JNIEnv * /* env */, jobject /* this */, jint outputDeviceId, jboolean bluetooth) {
    return engine().startPlayback(outputDeviceId, bluetooth);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_startRecording(JNIEnv *env, jobject /* this */, jstring path,
                                                               jstring referencePath, jlong countInFrames,
                                                               jint outputDeviceId, jint inputDeviceId, jboolean handsFreeInput, jboolean bluetooth,
                                                               jint inputChannel) {
    return engine().startRecording(toString(env, path), toString(env, referencePath), countInFrames,
                                   outputDeviceId, inputDeviceId, handsFreeInput, bluetooth,
                                   myaudio::toInputChannel(inputChannel));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_startMonitoring(JNIEnv * /* env */, jobject /* this */,
                                                                jint outputDeviceId, jint inputDeviceId,
                                                                jboolean handsFreeInput, jboolean bluetooth,
                                                                jint inputChannel) {
    return engine().startMonitoring(outputDeviceId, inputDeviceId, handsFreeInput, bluetooth,
                                    myaudio::toInputChannel(inputChannel));
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_setMonitorChain(JNIEnv *env, jobject /* this */, jfloatArray chain,
                                                                jfloat gain, jfloat pan, jboolean enabled) {
    // Built here, on the caller's thread: creating effects and estimating level matching must not run in the callback.
    engine().setMonitorChain(std::make_unique<myaudio::LiveChain>(readChain(env, chain), gain, pan, !enabled,
                                                                  myaudio::kProjectSampleRate));
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_setBufferSetting(JNIEnv * /* env */, jobject /* this */, jint setting) {
    myaudio::setBufferSetting(setting);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_currentDeviceIds(JNIEnv *env, jobject /* this */) {
    const auto ids = engine().currentDeviceIds();
    jintArray array = env->NewIntArray(2);
    if (array != nullptr) env->SetIntArrayRegion(array, 0, 2, ids.data());
    return array;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_transportXruns(JNIEnv * /* env */, jobject /* this */) {
    return engine().transportXruns();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_stop(JNIEnv * /* env */, jobject /* this */) {
    return engine().stopTransport();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_positionFrames(JNIEnv * /* env */, jobject /* this */) {
    return engine().positionFrames();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_isActive(JNIEnv * /* env */, jobject /* this */) {
    return engine().isActive();
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_setMetronome(JNIEnv * /* env */, jobject /* this */,
                                                             jboolean enabled, jfloat bpm, jfloat volume) {
    engine().setMetronome(enabled, bpm, volume);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_takeInputPeak(JNIEnv * /* env */, jobject /* this */) {
    return engine().takeInputPeak();
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_renderedMix(JNIEnv *env, jobject /* this */) {
    const std::vector<float> mix = engine().renderPlayback();
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(mix.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(mix.size()), mix.data());
    return array;
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_seekPlayback(JNIEnv * /* env */, jobject /* this */, jlong frame) {
    engine().seekPlayback(frame);
}

namespace {

jobjectArray toStringArray(JNIEnv *env, const std::vector<std::string> &values) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), stringClass, nullptr);
    for (size_t i = 0; array != nullptr && i < values.size(); ++i) {
        jstring value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(array, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(stringClass);
    return array;
}

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_airwindowsCatalog(JNIEnv *env, jobject /* this */) {
    // Name, category and description per effect, in catalog order (= index).
    std::vector<std::string> values;
    for (const myaudio::AirwindowsInfo &info : myaudio::airwindowsCatalog()) {
        values.emplace_back(info.name);
        values.emplace_back(info.category);
        values.emplace_back(info.description);
    }
    return toStringArray(env, values);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_airwindowsParameterNames(JNIEnv *env, jobject /* this */, jint index) {
    return toStringArray(env, myaudio::airwindowsParameters(index).names);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_airwindowsDefaults(JNIEnv *env, jobject /* this */, jint index) {
    const std::vector<float> defaults = myaudio::airwindowsParameters(index).defaults;
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(defaults.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(defaults.size()), defaults.data());
    return array;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_airwindowsDisplay(JNIEnv *env, jobject /* this */, jint index,
                                                                  jfloatArray values) {
    std::vector<float> parameters(env->GetArrayLength(values));
    env->GetFloatArrayRegion(values, 0, static_cast<jsize>(parameters.size()), parameters.data());
    return toStringArray(env, myaudio::airwindowsDisplay(index, parameters));
}

extern "C" JNIEXPORT void JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_setLoop(JNIEnv * /* env */, jobject /* this */, jlong start, jlong end) {
    engine().setLoop(start, end);
}

// Effect preview (EffectPreview.h): independent of the transport, so it never disturbs the project mix.

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_previewTrack(JNIEnv *env, jobject /* this */, jobjectArray paths,
                                                             jlongArray frames, jfloatArray parameters, jobjectArray chains,
                                                             jobjectArray envelopes, jobjectArray envelopeGains,
                                                             jlong startFrame, jlong lengthFrames) {
    const auto sources = readSources(env, paths, frames, parameters, chains, envelopes, envelopeGains);
    return !sources.empty() && myaudio::EffectPreview::instance().setTrack(sources.front(), startFrame, lengthFrames);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_previewMix(JNIEnv *env, jobject /* this */, jobjectArray paths,
                                                           jlongArray frames, jfloatArray parameters, jobjectArray chains,
                                                           jobjectArray envelopes, jobjectArray envelopeGains,
                                                           jlong startFrame, jlong lengthFrames) {
    return myaudio::EffectPreview::instance().setMix(readSources(env, paths, frames, parameters, chains, envelopes, envelopeGains),
                                                     startFrame, lengthFrames);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_previewSamples(JNIEnv *env, jobject /* this */, jfloatArray samples,
                                                               jint channels) {
    const jsize count = env->GetArrayLength(samples);
    if (channels <= 0) return JNI_FALSE;
    std::vector<jfloat> values(count);
    env->GetFloatArrayRegion(samples, 0, count, values.data());
    return myaudio::EffectPreview::instance().setSamples(values.data(), values.size() / channels, channels);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_previewRender(JNIEnv *env, jobject /* this */, jfloatArray chain) {
    const std::vector<float> loop = myaudio::EffectPreview::instance().render(readChain(env, chain));
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(loop.size()));
    if (array != nullptr) env->SetFloatArrayRegion(array, 0, static_cast<jsize>(loop.size()), loop.data());
    return array;
}

// Imported tones (AmpCabinet.h): amp models and cabinet impulse responses.

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_inspectTone(JNIEnv *env, jobject /* this */, jstring path, jint kind) {
    const myaudio::ToneFileInfo info = myaudio::inspectToneFile(toString(env, path), static_cast<myaudio::ToneKind>(kind));
    if (!info.ok) MYAUDIO_LOGW("MyAudioTone", "Rejected tone file: %s", info.error.c_str());
    return toStringArray(env, {info.ok ? "1" : "0", info.error, std::to_string(info.sampleRate),
                               std::to_string(info.frames), info.metadata});
}

extern "C" JNIEXPORT jint JNICALL
Java_com_martensening_my_1audio_audio_NativeAudio_loadTone(JNIEnv *env, jobject /* this */, jstring path, jint kind) {
    return myaudio::loadTone(toString(env, path), static_cast<myaudio::ToneKind>(kind));
}
