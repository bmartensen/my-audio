/*
 * Build configuration of the vendored LAME library for Android (clang) and Windows (MSVC), in place
 * of the header that LAME's autoconf script generates. Part of My Audio, MIT; LAME itself is LGPL.
 */
#ifndef MY_AUDIO_LAME_CONFIG_H
#define MY_AUDIO_LAME_CONFIG_H

#define PACKAGE "lame"
#define PROTOTYPES 1
#define STDC_HEADERS 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_LIMITS_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDINT_H 1
#define HAVE_STRCHR 1
#define HAVE_MEMCPY 1

/* Faster logarithm with enough precision, as in LAME's own Windows configuration. */
#define USE_FAST_LOG 1

typedef float ieee754_float32_t;
typedef double ieee754_float64_t;

#endif
