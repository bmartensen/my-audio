cmake_minimum_required(VERSION 3.22)
project(myaudio_desktop LANGUAGES C CXX)

# Native engine of the desktop app: the shared DSP, mix and JNI bridge from the Android module
# plus the miniaudio transport. Produces myaudio.dll / libmyaudio.so for NativeAudio.
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# The C runtime goes into the DLL (also for libopus), so no Visual C++ redistributable is needed.
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded")

# libopus encodes the shared mix (Android uses MediaCodec). Official release, pinned by checksum,
# linked statically; BSD licence.
include(FetchContent)
set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
FetchContent_Declare(opus
        URL https://downloads.xiph.org/releases/opus/opus-1.5.2.tar.gz
        URL_HASH SHA256=65c1d2f78b9f2fb20082c38cbe47c951ad5839345876e46941612ee87f9a7ce1
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_MakeAvailable(opus)

set(SHARED_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp)
find_package(JNI REQUIRED)

# The Airwindows effects of the effect library (shared with Android).
add_subdirectory(${SHARED_DIR}/third_party/airwindows ${CMAKE_CURRENT_BINARY_DIR}/airwindows)

add_library(myaudio SHARED
        ${SHARED_DIR}/AirwindowsEffects.cpp
        ${SHARED_DIR}/BleedRemoval.cpp
        ${SHARED_DIR}/Correlation.cpp
        ${SHARED_DIR}/JniBridge.cpp
        ${SHARED_DIR}/Metronome.cpp
        ${SHARED_DIR}/EffectPreview.cpp
        ${SHARED_DIR}/ProjectMix.cpp
        ${SHARED_DIR}/TrackProcessing.cpp
        ${SHARED_DIR}/Wav.cpp
        DesktopCalibration.cpp
        DesktopCapture.cpp
        DesktopDecoder.cpp
        DesktopDevices.cpp
        DesktopEngine.cpp
        DesktopOpus.cpp
        MiniaudioImpl.c)

target_include_directories(myaudio PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/miniaudio
        ${SHARED_DIR}
        ${JNI_INCLUDE_DIRS})

if(MSVC)
    # UTF-8 sources, M_PI and friends, no min/max macros from windows.h; the DSP is fast arithmetic.
    target_compile_options(myaudio PRIVATE /utf-8 /W3 $<$<COMPILE_LANGUAGE:CXX>:/EHsc> /O2)
    target_compile_definitions(myaudio PRIVATE _USE_MATH_DEFINES NOMINMAX _CRT_SECURE_NO_WARNINGS)
    set(FAST_MATH /fp:fast)
else()
    set(FAST_MATH -ffast-math)
    target_compile_options(myaudio PRIVATE -O3 -Wall)
    target_link_libraries(myaudio PRIVATE pthread dl m)
endif()

# As on Android, only the pure DSP is built with fast math; the mix relies on NaN meaning "off".
set_source_files_properties(${SHARED_DIR}/Correlation.cpp ${SHARED_DIR}/TrackProcessing.cpp ${SHARED_DIR}/BleedRemoval.cpp
        PROPERTIES COMPILE_OPTIONS "${FAST_MATH}")

target_link_libraries(myaudio PRIVATE opus airwindows)
