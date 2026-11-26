# Current work

The current state of the app and the repository. Open work is in [GitHub Issues](https://github.com/bmartensen/my-audio/issues), version history in the [changelog](../CHANGELOG.md), checksums in each [GitHub release](https://github.com/bmartensen/my-audio/releases).

## Release

**My Audio 0.11.2 / versionCode 41** for Android and Windows, tag `v0.11.2` on `main`. Milestone [0.11.x](https://github.com/bmartensen/my-audio/milestone/2) sharpens the direct recording workflow: undo/redo instead of confirmations, a start marker for Play and Record, per-track latency and offset by value.

- Android: signed APK on the GitHub release, no `INTERNET` permission.
- Windows: the [Microsoft Store](microsoft-store.md) listing [9N3X8S3HC311](https://apps.microsoft.com/detail/9N3X8S3HC311) (MSIX, signed by Microsoft) and the unsigned MSI on the GitHub release, which also carries the source of the bundled Temurin 21.0.13 runtime. Both share the projects in *Documents/My Audio*.
- Pages and martensening.com link the APK of the release and the Store for Windows ([Pages and releases](site-publication.md)).

0.11.2 adds to 0.11.0: Back asks before closing a project with undo history ([#38](https://github.com/bmartensen/my-audio/issues/38)) and first closes open notes; Redo sits left of Undo so Undo keeps its place; track cards draw the take's span from its audible start to its end.

## Implemented state

The shared Kotlin/Compose module owns the project model, UI and session logic. C++ shares offline mixing and effects; Oboe and miniaudio provide Android and Windows transport. The app supports calibrated overdubbing, mono capture with Android USB input selection, stereo import, non-destructive effects, six-slot track/master chains, 478 Airwindows effects, amp models (NAM A1/A2) and cabinet IRs, loudness matching and A/B previews. The effect library lets each effect be auditioned with adjustable, unsaved parameters. Live monitoring runs a track chain in real time while recording and as Live audition in the library and track editors; takes stay dry. A global audio buffer setting applies to calibration, takes and live monitoring. Every project change can be undone while the project is open; Play and Record start at a start marker; each track has a latency and an offset by value. Projects keep fields and effects of newer app versions when saved. Notes, tuner, practice loops, envelopes, Opus sharing, project backups and SoundCraft session exchange complete the current scope. See [requirements](app-requirements.md) and [modeling](modeling.md).

`build.ps1` builds Android, Windows (MSI) and the Store package (`-Target WindowsStore`), checks prerequisites read-only and installs missing tools only on request. Debug APKs and unsigned MSIs need no publisher credentials; release signing accepts developer-owned keys. See [developer builds](developer-builds.md).

Original work is MIT-licensed. [`licenses/`](../licenses/README.md) holds the third-party inventory, full license texts with source hashes, resolved runtime coordinates and the reference Java runtime's module notices. About shows the project license, the inventory and all texts.

## Verification

Automated tests are the verification ([repository commands](repository-rules.md#build-and-automated-checks)); deviations noticed in use become issues.

- Shared desktop tests (JVM, two gated SoundCraft tests skip without their environment), Android JVM tests and Android lint run with `gradlew`. They cover the project format and version exchange with the verbatim 0.8.5/0.8.6 readers, history, timing, start-marker takes through the desktop native library, import and SoundCraft exchange.
- Native tests (`tools/native_tests.ps1`) run on an Android device or emulator: bleed removal, playback loop, volume envelope, stream warm-up, master tone, stereo mix, live chain and amp/cabinet.
- Releases pass the [publication audit](publication-checks.md) (`prepare_public_repo.py` and Gitleaks) and `check_site_downloads.ps1 -RequireGitHubRelease`.

## Known limits

- `native_tests.ps1` lists neither NAM nor a response file, so `stereo_mix_test` and `live_chain_test`, which link the Airwindows catalogue and `AmpCabinet.cpp` with NAM, fail on Windows at the command-line limit or at linking; they build through a response file with NAM added.
- The Store package fails only the optional blocked-executables test of the App Certification Kit (`cmd`/`reg` strings in JARs, `CreateProcess` in the Java runtime), which matters only for Windows S mode.
- SoundCraft approximates My Audio's EQ, compressor and reverb, so a bounced session is about 1.7 dB quieter than My Audio's −14 LUFS mix.
- Guide screenshots date from 0.9.18 and 0.10.4; the calibration image is an earlier capture.
- Pages is checked with a local Liquid/Markdown approximation, not a Jekyll build.
