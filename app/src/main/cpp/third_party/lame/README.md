# LAME

MP3 encoder for "Mix as MP3".

| Part | Source | Version | License |
|---|---|---|---|
| `libmp3lame/`, `include/lame.h`, `COPYING`, `LICENSE`, `README` | [The LAME Project](https://lame.sourceforge.io/), `lame-3.100.tar.gz` (SHA-256 `ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`) | 3.100 | LGPL-2.0-or-later |

Copied unmodified: the encoder sources of `libmp3lame/` without `mpglib_interface.c` (decoding) and `i386/`
(assembly), with `vector/lame_intrin.h`, which `fft.c` includes. `config/config.h` and `CMakeLists.txt` belong to
My Audio and replace LAME's autoconf configuration. The library is built as its own shared library, which the engine
links dynamically; this directory is its complete corresponding source, and the GitHub release of every version carries
it in the release's source archive.

To update, download a LAME release, check its checksum, copy the same paths, run the MP3 export test and update this
table and `licenses/`.
