# Current work

As of 2026-11-23: **My Audio 0.9.18 / versionCode 33, released** for Android and Windows: tag `v0.9.18` (commit `a3890a9`) and the published [GitHub release](https://github.com/bmartensen/my-audio/releases/tag/v0.9.18) with APK, MSI, checksums and the runtime source.

## Release 0.9.18

- Content: optional record track, plain full-width/full-height recording area with Record centred in landscape, confirmations for effect removal and envelope reset, consistent add buttons, library refinements, About linking the Pages privacy policy. See the [changelog](../CHANGELOG.md).
- Built with `build.ps1 -Target All -AndroidConfiguration Release`: `MyAudio-0.9.18.apk` (SHA-256 `92b1985f…f919022`) and `MyAudio-0.9.18.msi` (`0f06a880…4b7eaf6a`) in `.work/`. Installed on the contributor's Pixel 9 as an update over 0.9.17 (signature compatible, projects preserved); it reports 0.9.18 / 33. Final human acceptance on the phone is open.
- 104 shared desktop tests and 13 Android JVM tests pass without failures or skips; Android lint reports 0 errors and 14 warnings. No new listening or hardware audio acceptance was performed.
- The MSI bundles Temurin 21.0.13+9. Its source archive `OpenJDK21U-jdk-sources_21.0.13_9.tar.gz` is downloaded to `.work/runtime-source/` and matches Adoptium's published SHA-256 (`b5232c99…406ad1ca`). `tools/publish_github_release.ps1 -Publish` uploaded it with the installers; `check_site_downloads.ps1 -RequireGitHubRelease` passes.

## Pages and downloads

- Pages moved to symmetric `/de/…` and `/en/…` addresses with privacy policy and legal notice; `/` chooses the language and `/anleitung/` redirects. Guide, landing, privacy and legal pages follow the guide content rule in [decisions](decisions.md): what each control does, no design details, no semicolons, bold for controls.
- Screenshots are from 0.9.18 on the Pixel 9 with the synthetic demo project and status bar demo mode (restored afterwards). The demo project's notes size on the phone is one step smaller. The calibration image is the earlier Pixel capture: the current route was uncalibrated and no calibration was run.
- Local preview (Liquid/Markdown approximation, not a GitHub Jekyll build): all eight pages at 320, 390 and 1440 px without overflow, missing images or broken anchors; root and `/anleitung/` redirects verified in German and English browser locales. `test_site_downloads.ps1` passes seven rejection cases. `release_downloads` is `true`: the landing pages link the release assets directly.
- Pages no longer links a mirror: with `release_downloads: false` the landing pages fall back to `releases/latest`, which `check_site_downloads.ps1` then requires to be a complete release. Both privacy policies describe martensening.com only as the product page linked from About. Checked with `test_site_downloads.ps1`, `check_site_downloads.ps1` and the `releases/latest` fallback assertion; Pages was not built locally with Jekyll.
- martensening.com (separate private repository): the product page now links the GitHub release assets and the repository no longer contains installers; its shared privacy policy covers the website, My Audio and Zufalling. Its operator deploys it manually; this wiki does not track the server state.
- The 0.9.18 installers stay unchanged: the notice addition about earlier installers (`licenses/THIRD_PARTY_NOTICES.md`) reaches About with the next regular build.
- Legal check 2026-11-23: neither Pages nor martensening.com sets cookies or loads third-party fonts or scripts (live responses), and the merged release manifest requests no `INTERNET` permission.

## Implemented state

The shared Kotlin/Compose module owns the project model, UI and session logic. C++ shares offline mixing and effects; Oboe and miniaudio provide Android and Windows transport. The app supports calibrated overdubbing, mono capture with Android USB input selection, stereo import, non-destructive effects, six-slot track/master chains, 478 Airwindows effects, loudness matching and A/B previews. The effect library lets each effect be auditioned with adjustable, unsaved parameters. The last take supports audio-only deletion for retakes, and a prepared empty record track can be removed completely; recording works without calibration and marks such takes. Live monitoring runs a track chain in real time while recording (unmuted record track) and as Live audition in the library and track editors; takes stay dry. A global audio buffer setting applies to calibration, takes and live monitoring. Projects keep fields and effects of newer app versions when saved. Notes, tuner, practice loops, envelopes, Opus sharing and project backups complete the current scope. See [requirements](app-requirements.md) and [modeling](modeling.md).

`build.ps1` builds Android and Windows, checks prerequisites read-only and installs missing tools only on request. Debug APKs and unsigned MSIs need no publisher credentials; release signing accepts developer-owned keys. See [developer builds](developer-builds.md).

Original work is MIT-licensed. [`licenses/`](../licenses/README.md) holds the third-party inventory, full license texts with source hashes, resolved runtime coordinates and the reference Java runtime's module notices. About shows the project license, the inventory and all texts.

## Verification

- 0.9.18: 104 shared desktop tests and 13 Android JVM tests pass without failures or skips, including version exchange against the verbatim 0.8.5/0.8.6 reader and the optional record track; Android lint reports 0 errors and 14 warnings. Release APK installed as a signature-compatible update on the Pixel 9.
- All eight native tests, including `live_chain_test` (live chain and monitor sample-identical to the rendered chain), passed on the x86_64 emulator with 0.9.x native code, which 0.9.18 does not change; they have not yet run on the arm64 Pixel 9.
- Before publishing 0.9.18, `prepare_public_repo.py` inspected 1,793 files and Gitleaks found no leaks in the copied candidate; the findings in the working tree were all in ignored local files (hash manifests in `.work/`, a JDK configuration under `build/`).

These are build and automated results, not hardware acceptance. Live latency, dropouts, CPU, listening and routing checks on the Pixel 9 and Windows are open; see the [backlog](backlog.md).
