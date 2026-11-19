# Durable decisions

Newest dated group first. Entries describe choices that currently govern the project; detailed behavior belongs in requirements and topic pages.

## 2026-11-19

- **Product:** a studio from the smartphone for spontaneous musical ideas, multitrack sketches and demos with effects. A direct recording workflow takes priority over competing with professional DAWs; Windows complements the phone.
- **Collaboration:** one English repository-local wiki and one shared policy, usable from several assistant clients; conversation can use any language. Credit the people and communities whose work the app uses and keep their notices; assistants carry no authorship labels. See [collaboration](collaboration.md).
- **Skills:** canonical workflows live in `.agents/skills/`, which Antigravity, Gemini CLI and Codex discover directly; only Claude Code needs delegating adapters (`.claude/skills/`).
- **License:** original project code/documentation use MIT; dependencies and media retain their own licenses. The inventory and full texts live in `licenses/` and form the app's About license view.
- **Developer access:** Android debug and unsigned Windows builds require no publisher credentials. Tool installation is explicit; JDK/SDK discovery is portable. Android release signing accepts developer-owned secrets without an owner dependency, while preserving existing local configurations.
- **Public repository:** starts at 0.8.5 / code 13 from a reviewed source-only export, without earlier history, secrets or personal recordings. Commit/push authorization is specific to each checkout and task. Forks are supported without a repository-owner allowlist.
- **Effect preview:** short offline-rendered clips use the same native processing as the mix; looping playback, A/B and debounced parameter updates provide audible feedback. Use project audio where available, otherwise the credited CC0 sample. Live monitored effects are separate work.
- **Effect chains:** up to six ordered slots per track/master, bypass retaining parameters, stable effect IDs and local library selection. Airwindows level matching defaults on to preserve balance. Fixed final master loudness/limiting remains independent of slots.
- **USB capture:** Android two-channel devices offer input 1, input 2 or their sum, device-specific and shared by calibration/tuner. Saved takes remain mono.

## 2026-11-18

- **Stereo:** imported mono/stereo channel counts are preserved through decoding, resampling, WAV, effects and export. Stereo balance attenuates the opposite channel; linked normalization/compression preserve imaging. Native recording and overdub references remain mono.
- **Cleanup defaults:** new takes/imports explicitly disable click and bleed removal; overdub references remain available. Missing fields in existing project formats retain their defined compatibility defaults.

## 2026-11-09

- **Shared platforms:** Compose Multiplatform and Kotlin share UI/data/session logic, C++ shares audio processing. Android uses Oboe, Windows uses miniaudio. Versions are synchronized. Linux is a later target; macOS is undecided.
- **Windows workflow:** same functional layout with precise mouse editing, native file dialogs and project storage in Documents/My Audio. Per-user MSI includes a Java runtime and leaves projects intact on uninstall. Window position/size persists when the screen remains available.
- **Imports:** a ZIP with project.json is a backup; without it, supported audio entries become aligned tracks in one new project. Conversion completes before committing the project. File paths in archives are never trusted as output paths.
- **Musical aids:** per-project notes with chord highlighting/scrolling, guitar tuner and optional silent pre-roll support preparation without requiring a recording. Notes are preserved verbatim in backups.

## 2026-11-05

- **Sharing:** mixes use stereo Ogg/Opus at 48 kHz; the application writes the container for consistent playback in receiving applications. Track exports are aligned cleaned WAVs without creative effects/gain/pan; backups preserve originals and settings.
- **Releases:** versionName and versionCode live in gradle.properties; Android and desktop share the version. Every distributed build gets a new patch version and versionCode. Artifact hashes and release-signature checks accompany release packaging.

## 2026-10-30

- **Calibration:** validate each actual output/input pair acoustically before recording; accept at most 5 ms spread for internal/wired routes or 25 ms for Bluetooth. Reconnection invalidates Bluetooth calibration. Store the measured correction with each take so recalibration cannot move existing audio.
- **Bluetooth:** music-profile output and internal microphone are the default-quality route. Selecting a headset microphone uses a non-Bluetooth output; recording and music playback on the same headset are not combined. Verify actual opened devices.
- **Bleed:** retain the short reference filter, clipping-aware fitting and conservative local gain tracking. Do not alter source audio, shift takes or apply a noise gate. Measurements and listening evidence have distinct limits.

## 2026-10-29

- **Project model:** immutable 48 kHz PCM16 originals plus typed non-destructive settings and disposable versioned caches. Save each take on stop; deletion requires confirmation. No take lists, clip arrangement, punch-in or loop recording.
- **Transport:** existing tracks play while a new mono track records; preparation may render offline. Practice loops affect playback only. Android continues transport in the background with an explicit stop notification; interruptions save and stop without automatic restart.
- **Mix:** track cleanup/normalization/chain precede gain/envelope/pan; the master chain precedes −14 LUFS normalization (maximum +24 dB) and a −1 dBFS limiter. The metronome is added afterward.
- **Compatibility:** optional fields have defined defaults; semantic changes require format versioning and migration. Golden-file tests preserve existing user projects rather than updating fixtures to hide incompatibilities.
