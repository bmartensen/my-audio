# Project Guide

What is specific to My Audio. The shared policy is `AGENTS_GUIDE.md`, which comes from the [harness](.agents/harness.md) at the version recorded there; this guide adds to it and states deliberate deviations with their reason. Keep it short: knowledge belongs in `.wiki/`, open work in GitHub Issues.

## Product Direction
- **My Audio** is a studio from the smartphone for spontaneous musical ideas, multitrack sketches and demos with effects, on Android and Windows. A direct recording workflow comes first; competing with professional DAWs is not the goal.
- Android is the primary target; Windows complements the smartphone-first scope. Linux is a later target; macOS is undecided.
- Kotlin/Compose Multiplatform shares UI and data; C++ shares audio processing, with Oboe on Android and miniaudio on Windows.
- The interface languages are German and English; interface text is drafted in both ([interface text](.wiki/interface-text.md)).

## Issue Labels
The harness labels are in `.wiki/repository-rules.md#issues`. Project labels, synced the same way (`.wiki/repository-rules.md#label-sync`):

| Label | Color | Description |
|---|---|---|
| `android` | `3ddc84` | Android only |
| `windows` | `0078d4` | Windows only |
| `audio` | `5319e7` | Recording, playback, calibration and processing |
| `effects` | `d93f0b` | Effects, library and preview |

## Builds and Releases
- Build, test, device and publishing commands: `.wiki/development.md`. Start with `build.ps1 -Target All -Check` to inspect prerequisites, then `build.ps1 -Target All -InstallTools` when installation is authorized; `Android`, `Windows` and `WindowsStore` targets are available separately.
- The helpers discover JDK 21 and SDK paths; Python and Android Studio are optional.
- `gradle.properties` is the shared source of versionName and versionCode for Android and Windows. Raise the patch version and versionCode before every distributed build; a milestone covers a minor version (e.g. `0.11.x`).
- Ordinary development needs no publisher credentials: `:app:assembleDebug` uses Android's local debug key and Windows development installers are unsigned. Do not request or copy the publisher's release key, the `martensening*` properties or `MY_AUDIO_SIGNING_CERT_SHA256` just to build the app.
- Debug and release share the application ID `com.martensening.my_audio`. On a signature mismatch, prefer an emulator; never uninstall the user's app, because that deletes its projects.
- GitHub Pages builds from `main` → `/docs`, so the site always describes the released app. Publishing a version: `.wiki/site-publication.md#publishing-a-version`; Microsoft Store: `.wiki/microsoft-store.md`.

## Additional Rules
- The license inventory is `licenses/THIRD_PARTY_NOTICES.md` with the full texts and provenance in `licenses/`; the in-app About credits are generated from them. Update all of them in the same change as a dependency, vendored code or media.
- Never version personal recordings or analysis results; outputs, logs, screenshots and test projects stay in the ignored `.work/`.

## Deviations from AGENTS_GUIDE.md
- None.
