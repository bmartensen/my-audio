#pragma once

// Logging for the shared native code: logcat on Android, standard error elsewhere (desktop).
#if defined(__ANDROID__)
#include <android/log.h>
#define MYAUDIO_LOGI(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)
#define MYAUDIO_LOGW(tag, ...) __android_log_print(ANDROID_LOG_WARN, tag, __VA_ARGS__)
#else
#include <cstdio>
#define MYAUDIO_LOG(level, tag, ...)                  \
    do {                                              \
        std::fprintf(stderr, "%c/%s: ", level, tag);  \
        std::fprintf(stderr, __VA_ARGS__);            \
        std::fputc('\n', stderr);                     \
        std::fflush(stderr);                          \
    } while (0)
#define MYAUDIO_LOGI(tag, ...) MYAUDIO_LOG('I', tag, __VA_ARGS__)
#define MYAUDIO_LOGW(tag, ...) MYAUDIO_LOG('W', tag, __VA_ARGS__)
#endif
