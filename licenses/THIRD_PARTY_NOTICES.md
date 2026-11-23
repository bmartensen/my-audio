# Credits and third-party notices

My Audio is made possible by the work of many people and open-source communities. Thank you for sharing that work. Original My Audio code/documentation are MIT-licensed under LICENSE; this does not replace the licenses of the components below. Copyright and license notices in vendored files remain authoritative.

This inventory lives in the repository's licenses/ directory beside the full texts. The application's About → Open-source licenses view includes LICENSE, this inventory and all license texts under licenses/. The same notice set is used on both platforms; platform-specific components are identified below. The exact resolved runtime coordinates, including debug tooling, are recorded in licenses/runtime-components.txt.

## Shared UI and application libraries

- **Kotlin standard library** (2.3.20 resolved runtime), **kotlinx.coroutines** (Android 1.9.0, desktop 1.10.2), **kotlinx.serialization** (1.9.0), **kotlinx-datetime** (0.7.1) and **atomicfu** (0.28.0): JetBrains s.r.o. and contributors, Apache-2.0.
- **Compose Multiplatform** (1.12.2, desktop Material 3 1.9.0), **Jetpack Compose** and **AndroidX** activity, core, lifecycle, savedstate, navigationevent, collection, annotation, window, graphics, tracing, startup, profileinstaller, emoji, autofill and compatibility libraries: The Android Open Source Project, JetBrains s.r.o. and contributors, Apache-2.0. Transitive versions are listed in the coordinate inventory.
- **JetBrains annotations** (23.0.0), **JetBrains Runtime API** (1.9.0), **JSpecify** (1.0.0) and **Guava ListenableFuture** (1.0): respective JetBrains, JSpecify and Google contributors, Apache-2.0. Android/JVM artifact notices are preserved in licenses/jvm-artifact-notices.txt; the complete Apache-2.0 terms are included in the license set.
- **Material Icons** by Google: most interface icons (vector drawables under shared/src/commonMain/composeResources/drawable/) are taken or adapted from Google's Material icon set, Apache-2.0. The metronome, tuner, solo, envelope and notes icons and all launcher/brand artwork are original My Audio designs.

## Audio

- **Airwindows** by Chris Johnson and **Airwin Consolidated** by Paul Walker: MIT. Embedded source is from airwin2rack commit 59a227eba3c8b3070abd7f86a28c54685dfa3b76, with 478 selected effects. Catalogue selection and generated integration are documented beside the vendored code. Both authors' credits and full notices are retained.
- **NeuralAmpModelerCore v0.6.0** (Neural Amp Modeler inference for imported amp models) by Steven Atkinson and contributors: MIT. Vendored unmodified with **Eigen 5.0.1** (Eigen contributors; MPL-2.0, some files BSD-3-Clause, Apache-2.0 or MINPACK as listed in its COPYING files; the complete source of the included Eigen files is in this repository) and **JSON for Modern C++ 3.12.0** by Niels Lohmann (MIT). Provenance: app/src/main/cpp/third_party/nam/README.md. Amp models and impulse responses are not part of My Audio; users import files they obtained themselves, under those files' own terms.
- **Oboe 1.11.0** (Android): The Android Open Source Project, Apache-2.0.
- **Android LLVM/libc++ runtime**: LLVM contributors and the additional authors named in licenses/android-llvm-NOTICE.txt, Apache-2.0 with LLVM exceptions and the included third-party notices. The notice is taken from NDK 30.0.16248370.
- **miniaudio 0.11.25** (Windows): David Reid, MIT No Attribution or Unlicense at the recipient's choice; includes its embedded dr_* decoders. **stb_vorbis**: Sean Barrett, MIT or Unlicense. The full headers and notice texts accompany the source.
- **libopus 1.5.2** (Windows): Xiph.Org, Skype, Octasic, Jean-Marc Valin, Timothy B. Terriberry, CSIRO, Gregory Maxwell, Mark Borgerding, Erik de Castro Lopo, Mozilla, Amazon and contributors, BSD-3-Clause. CMake fetches the official source archive with a pinned SHA-256.

## Windows graphics

- **Skiko 0.150.2**: JetBrains s.r.o., Apache-2.0, including its NOTICE for adapted Android Open Source Project code.
- **Skia m150-a3f509bf52**: Google Inc. and contributors, BSD-3-Clause. This is the Skia revision pinned by Skiko 0.150.2.
- Skia's Windows static dependencies include **HarfBuzz** (Old MIT), **ICU** (Unicode License v3 plus included notices), **libpng** (PNG Reference Library License), **zlib** (zlib license), **libjpeg-turbo** (IJG/BSD/zlib), **libwebp** (BSD and patent grant), **Expat** (MIT) and **D3D12 Memory Allocator** (MIT, Advanced Micro Devices). Complete texts are included under licenses/, with exact revisions/URLs and hashes in its provenance files.

**This software is based in part on the work of the Independent JPEG Group.** The named projects and authors do not endorse My Audio.

## Packaged Java runtime

The Windows MSI includes a jlink runtime selected by the build environment. The verified build uses **Eclipse Temurin 21.0.13+9 / OpenJDK 21**, copyright Oracle and/or its affiliates and OpenJDK contributors, GPLv2 with the Classpath Exception and the runtime's additional notices. The exact installed runtime/legal directory is included with the application; a reference copy of those texts is also available in About. The Classpath Exception applies to independent application code using the runtime.

Runtime source revision: https://github.com/adoptium/jdk21u/tree/2ab10ca20c19
Build source revision: https://github.com/adoptium/temurin-build/tree/1cf43d7a3e77886bfea7d550a902e151a87d724d

Every published MSI from 0.8.4 to 0.9.18 bundles this same Temurin 21.0.13+9 runtime. Its complete source archive (OpenJDK21U-jdk-sources_21.0.13_9.tar.gz) is attached to the GitHub release v0.9.18 of My Audio, which also covers the installers distributed earlier: https://github.com/bmartensen/my-audio/releases/tag/v0.9.18

Redistributors must provide a compliant corresponding-source route for their selected runtime. See .wiki/developer-builds.md; do not treat this upstream reference or the JDK src.zip as fulfillment by itself. Other JDK selections require checking their exact notices and distribution terms. Local build preparation does not publish installers.

## Preview music

**“Short Jazzy Tune No.1” by painted-panda**, Freesound sound 609157:
https://freesound.org/people/painted-panda/sounds/609157/

Released under **CC0 1.0 Universal** (verified 2026-11-19). The bundled preview is converted from the preview MP3 to 48 kHz stereo PCM16 and lowered by 1 dB. We credit the artist with appreciation although CC0 does not require attribution. Full CC0 legal text is included.

## Development and source distribution

The repository includes the **Gradle 9.4.1 wrapper**, Apache-2.0; its notice text is included. CMake, Ninja, Android SDK/NDK, Kotlin/Android/Compose Gradle plugins, Microsoft Visual Studio C++ tools, Python, Pillow and optional NumPy/SciPy support development. Downloaded tools remain subject to their own licenses and are not redistributed as part of the source export. Microsoft's compiled runtime components follow the Visual Studio redistributable-code terms. Keep vendor notices with any separately redistributed tool/runtime package.

License sources and checksums: licenses/README.md and licenses/sources.json. Upgrading dependencies or adding borrowed work requires updating the inventory and credits together with the code.
