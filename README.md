<p align="center"><img src="branding/app-icon-512.png" width="160" alt="My Audio microphone and piano logo"></p>

# My Audio

**A studio from your smartphone. Your idea. Your demo.**

My Audio records musical ideas as multitrack sketches on Android and Windows: record a melody, add a guitar part or a second voice over it, try effects and share the mix. It focuses on a direct path from idea to demo, not on competing with professional audio workstations.

**Version 0.8.5** for Android (API 29+) and Windows x64. Hardware acceptance and known limitations are tracked in [current work](.wiki/current-work.md) and the [backlog](.wiki/backlog.md).

## Features

- Calibrated overdubbing: existing tracks play while a new one records; measured latency keeps takes aligned. Works with the built-in speaker and microphone; headphones avoid speaker bleed.
- Import of MP3, Ogg Vorbis/Opus and WAV, preserving mono or stereo.
- Gain, pan/balance, volume envelopes, solo/mute and a practice loop.
- Up to six effects per track and on the master: My Audio EQ/compressor and reverb plus 478 [Airwindows](https://www.airwindows.com/) effects, with looping A/B previews.
- Metronome with count-in, silent pre-roll, guitar tuner and project notes with chord highlighting and scrolling.
- Non-destructive processing: original takes never change.
- Sharing as an Ogg/Opus mix, aligned individual WAV tracks or a complete project backup ZIP.
- English and German interface, light and dark themes. Everything runs locally, without an account.

The agreed scope is in the [requirements](.wiki/app-requirements.md), version history in the [changelog](CHANGELOG.md).

## Building

On Windows x64, in PowerShell from the checkout:

```powershell
# Check prerequisites without changing anything:
.\build.ps1 -Target All -Check

# Install missing tools, then build an Android debug APK and a Windows MSI:
.\build.ps1 -Target All -InstallTools
```

The helper finds or installs JDK 21, the pinned Android SDK/NDK/CMake packages and the Visual Studio C++ Build Tools (via `winget`). Android Studio and Python are not required. Use `-Target Android` or `-Target Windows` for one platform. Artifacts and SHA-256 files go to `.work/`; nothing is installed on a device.

No publisher credentials are needed: debug APKs use Android's local debug key, and development MSIs are unsigned. To sign Android releases with your own key, see [developer builds](.wiki/developer-builds.md). Test commands are in [repository commands](.wiki/repository-rules.md).

## Architecture

Kotlin and Compose Multiplatform share the UI, project model and session logic. A C++ engine shares mixing and effects; Oboe provides Android audio and miniaudio Windows audio.

| Directory | Contents |
|---|---|
| [`shared/`](shared/) | Project model, UI, session logic and resources |
| [`app/`](app/) | Android host and Oboe; shared native processing under `src/main/cpp` |
| [`desktop/`](desktop/) | Windows host, miniaudio and installer configuration |
| [`tools/`](tools/) | Tool discovery, builds, native tests and release checks |
| [`.wiki/`](.wiki/README.md) | Requirements, decisions, backlog and technical documentation |

[Modeling](.wiki/modeling.md) explains immutable source audio, processing parameters, caches, frame-based timing and the versioned project format.

## Working on the project

Project knowledge lives in the [wiki](.wiki/README.md): agreed behavior in [requirements](.wiki/app-requirements.md), choices in [decisions](.wiki/decisions.md), open work in the [backlog](.wiki/backlog.md). The wiki is updated with each meaningful change. Please read the [collaboration principles](.wiki/collaboration.md) before contributing.

[`AGENTS.md`](AGENTS.md) is the shared policy for contributors and for coding assistants (Antigravity, Claude Code, Gemini CLI, OpenAI Codex). Three shared workflows live in [`.agents/skills/`](.agents/README.md): `start` orients a session, `grill-me` settles a decision and records it, `wrap` verifies and saves work.

## License and credits

Original My Audio code and documentation are licensed under the [MIT License](LICENSE). Third-party components and the preview sample keep their own licenses; see the [third-party notices](licenses/THIRD_PARTY_NOTICES.md) and [license texts](licenses/README.md). The app shows them under **About → Open-source licenses**.

Thank you to the communities behind Kotlin, Compose, AndroidX, Google's Material Icons, Oboe, Airwindows/Airwin Consolidated, miniaudio, Opus, Skiko/Skia and OpenJDK, and to painted-panda for the CC0 preview music. This software is based in part on the work of the Independent JPEG Group.

[Product information and contact](https://www.martensening.com/myaudio/) · [Branding](branding/README.md)
