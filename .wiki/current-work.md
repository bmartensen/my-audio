# Current work

As of 2026-11-22: **My Audio 0.9.0 / versionCode 15**, Android and Windows.

## Implemented state

The shared Kotlin/Compose module owns the project model, UI and session logic. C++ shares offline mixing and effects; Oboe and miniaudio provide Android and Windows transport. The app supports calibrated overdubbing, mono capture with Android USB input selection, stereo import, non-destructive effects, six-slot track/master chains, 478 Airwindows effects, loudness matching and A/B previews. The effect library lets each effect be auditioned with adjustable, unsaved parameters. The last track is an always-present record track with two-stage deletion; recording works without calibration and marks such takes. Live monitoring runs a track chain in real time while recording (unmuted record track) and as Live audition in the library and track editors; takes stay dry. A global audio buffer setting applies to calibration, takes and live monitoring. Projects keep fields and effects of newer app versions when saved. Notes, tuner, practice loops, envelopes, Opus sharing and project backups complete the current scope. See [requirements](app-requirements.md) and [modeling](modeling.md).

`build.ps1` builds Android and Windows, checks prerequisites read-only and installs missing tools only on request. Debug APKs and unsigned MSIs need no publisher credentials; release signing accepts developer-owned keys. See [developer builds](developer-builds.md).

Original work is MIT-licensed. [`licenses/`](../licenses/README.md) holds the third-party inventory, full license texts with source hashes, resolved runtime coordinates and the reference Java runtime's module notices. About shows the project license, the inventory and all texts.

## Verification

- 0.9.0 / versionCode 15: signed Android release APK (`.work/MyAudio-0.9.0.apk`, V2 signature verified against the configured release keystore by `release_apk.ps1`) and unsigned Windows MSI (`.work/MyAudio-0.9.0.msi`), SHA-256 files next to both. Built from the uncommitted working tree; the `v0.9.0` tag follows when that state is committed.
- 103 shared desktop tests and 13 Android JVM tests pass without failures or skips, including version exchange against the verbatim 0.8.5/0.8.6 reader and record-track repository tests. Android lint reports 0 errors and 14 warnings.
- All eight native tests, including the new `live_chain_test` (live chain and monitor sample-identical to the rendered chain, input untouched), pass on the x86_64 emulator; they have not yet run on the arm64 Pixel 9.
- Emulator UI checks: record track, two-stage trash, reset, recording into the empty record track and without calibration (offset 0, mark stored), buffer setting, Live start with loudspeaker warning and stop. The emulator says nothing about latency, CPU or sound.
- The 0.8.6 source-export audit and Gitleaks result predate these changes.

These are build and automated results, not hardware acceptance. Live latency, dropouts, CPU, listening and routing checks on the Pixel 9 and Windows are open; see the [backlog](backlog.md).

## Before distributing binaries

Supply the matching Java runtime's corresponding source with the MSI and keep its `runtime/legal` directory; see [developer builds](developer-builds.md#distribution-and-licenses).
