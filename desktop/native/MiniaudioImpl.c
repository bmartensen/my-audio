// The single translation unit that compiles miniaudio (third_party/miniaudio, v0.11.25) with
// Ogg Vorbis support from stb_vorbis (same tag, extras/). Devices drive the engine; the decoders
// read MP3, WAV, FLAC and Ogg Vorbis for "Import track" and audio ZIPs.
#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

#define MA_DR_MP3_FLOAT_OUTPUT  // Keep MP3 precision until the final WAV quantisation.
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#undef STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"
