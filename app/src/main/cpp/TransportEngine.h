#pragma once

// The real-time transport of the platform behind the shared JNI bridge: Oboe on Android,
// miniaudio on the desktop (desktop/native). Both offer the same public interface.
#if defined(__ANDROID__)
#include "MultitrackEngine.h"
namespace myaudio { using TransportEngine = MultitrackEngine; }
#else
#include "DesktopEngine.h"
namespace myaudio { using TransportEngine = DesktopEngine; }
#endif
