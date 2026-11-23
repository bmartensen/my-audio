cmake_minimum_required(VERSION 3.22.1)
project(myaudio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

# The Airwindows effects of the effect library, built as their own optimised static library.
add_subdirectory(third_party/airwindows)
# Neural Amp Modeler inference for imported amp models (C++20 object library).
add_subdirectory(third_party/nam)

add_library(myaudio SHARED
        AirwindowsEffects.cpp
        AmpCabinet.cpp
        CalibrationSession.cpp
        DawExport.cpp
        Correlation.cpp
        JniBridge.cpp
        LiveChain.cpp
        Metronome.cpp
        MultitrackEngine.cpp
        EffectPreview.cpp
        ProjectMix.cpp
        TrackProcessing.cpp
        BleedRemoval.cpp
        Wav.cpp)

target_compile_options(myaudio PRIVATE -Wall -Wextra)
# Offline DSP must stay fast in debug builds too; it is pure arithmetic.
# LiveChain runs the same DSP in the audio callback; same flags, so live and rendered sound alike.
set_source_files_properties(AmpCabinet.cpp Correlation.cpp TrackProcessing.cpp BleedRemoval.cpp LiveChain.cpp PROPERTIES COMPILE_OPTIONS "-O3;-ffast-math")

target_link_libraries(myaudio oboe::oboe log airwindows nam)
