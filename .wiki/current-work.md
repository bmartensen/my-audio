# Current work

As of 2026-11-19: **My Audio 0.8.5 / versionCode 13**, Android and Windows.

## Implemented state

The shared Kotlin/Compose module owns the project model, UI and session logic. C++ shares offline mixing and effects; Oboe and miniaudio provide Android and Windows transport. The app supports calibrated overdubbing, mono capture with Android USB input selection, stereo import, non-destructive effects, six-slot track/master chains, 478 Airwindows effects, loudness matching and A/B previews. Notes, tuner, practice loops, envelopes, Opus sharing and project backups complete the current scope. See [requirements](app-requirements.md) and [modeling](modeling.md).

`build.ps1` builds Android and Windows, checks prerequisites read-only and installs missing tools only on request. Debug APKs and unsigned MSIs need no publisher credentials; release signing accepts developer-owned keys. See [developer builds](developer-builds.md).

Original work is MIT-licensed. [`licenses/`](../licenses/README.md) holds the third-party inventory, full license texts with source hashes, resolved runtime coordinates and the reference Java runtime's module notices. About shows the project license, the inventory and all texts.

## Verification

- Signed Android release APK and Windows MSI build for 0.8.5. The APK reports versionCode 13 and its certificate matches the configured release keystore. The About license text starts with LICENSE and the inventory.
- Debug APK builds; 87 shared desktop tests and 13 Android JVM tests pass without failures or skips. Android lint reports 0 errors and 14 warnings (dependency/target-API updates and one densityless bitmap).
- The source-export audit passes its 8 tests; the exported 1,739-file candidate is hash-verified and Gitleaks 8.30.1 reports no findings. All relative Markdown links resolve.

These are build and automated results, not hardware acceptance. Real-device listening and routing checks, clean-machine tool installation and interactive skill invocation in each client are open; see the [backlog](backlog.md).

## Before distributing binaries

Supply the matching Java runtime's corresponding source with the MSI and keep its `runtime/legal` directory; see [developer builds](developer-builds.md#distribution-and-licenses).
