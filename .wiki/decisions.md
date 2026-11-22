# Durable decisions

Newest dated group first. Entries describe choices that currently govern the project; detailed behavior belongs in requirements and topic pages.

## 2026-11-22

- **Brief interface text:** settings and dialogs show labels and at most one short line; explanations belong in the user guide on GitHub Pages (`docs/`), which links the GitHub repository.
- **Audio buffer:** one global setting for calibration, takes and live monitoring; offered as Automatic | Short | Long (a medium step equals Automatic on Android). Automatic keeps the system default and existing calibrations, every other choice has its own calibration because the round trip changes with it.
- **Deterministic Airwindows:** instances are created with a fixed `rand()` seed, so mix renders are reproducible and live monitoring sounds exactly like the render.
- **Format compatibility:** maximum exchange between app versions in both directions. New versions always read every earlier project; older versions open newer projects. Features are added as optional fields within the lowest required format version. The version is raised only for an unavoidable semantic change, because installed apps reject higher versions. From 0.9, fields the app does not know are preserved verbatim on save at every level, so editing in an older app loses nothing. Effect types the app does not know open as preserved, silent slots with a “requires a newer version” hint, instead of rejecting the project. 0.9 therefore stays within formats 1–3: the empty record track is an optional project field outside `tracks`, and the uncalibrated mark is an optional track field. Installed 0.8.x apps open such projects but drop these fields when they save.
- **Record track (0.9):** the last track is the record track and always exists, empty in a new project. Record fills it when empty; when it holds a take, Record appends a new track that inherits its effect chain and input settings. Its trash is two-stage: “Delete recording?” removes the take but keeps the settings, so retakes run through the same effects; on an empty track “Reset?” restores the defaults. The trash is disabled while the record track is empty and at defaults, which makes that state visible. Older tracks keep single-confirmation whole-track deletion, so no unrecordable empty tracks remain mid-project. A “+ empty track” action lets the next instrument be prepared before recording.
- **Recording without calibration (0.9):** recording is never blocked by a missing calibration when the device can capture; the app warns and keeps the calibration banner instead. Such takes are stored without latency compensation (offset only for the count-in) and marked as uncalibrated in an optional track field, with a hint on the track. Nothing shifts automatically; a later manual timing correction may build on the mark.
- **Live effects (0.9):** the measured round trip (~16 ms on the reference Pixel) allows real-time effect monitoring. The record track's Mute switch controls monitoring: every newly created track starts muted, unmuting it monitors the microphone (dry or through its chain), and saving a take unmutes the track automatically so the take is heard. Deleting only the recording keeps the Mute state like every other setting until it is changed or reset. While recording with monitoring, the microphone runs through the record track's chain to the output; the take stays dry and receives the same chain offline after stop. In the library and track effect editors a Live switch complements the clip preview, sending only the microphone through the effect to the output without project playback or metronome. Loudspeaker (feedback) and Bluetooth (150–300 ms) routes warn before Live starts but are not blocked.

- **Saving to Git:** assistants commit or push only on an explicit request or through `wrap`; the public repository must not receive accidental commits.
- **Version tags:** every version gets an annotated tag `v<versionName>` on the commit it was built from, so published installers map to exact source.
- **Effect editing:** library auditions expose the effect's parameters without storing them, so effects can be judged beyond their conservative defaults. Effect dialogs use the full phone width.

## 2026-11-19

- **Product:** a studio from the smartphone for spontaneous musical ideas, multitrack sketches and demos with effects. A direct recording workflow takes priority over competing with professional DAWs; Windows complements the phone.
- **Collaboration:** one English repository-local wiki and one shared policy, usable from several assistant clients; conversation can use any language. Credit the people and communities whose work the app uses and keep their notices; assistants carry no authorship labels. See [collaboration](collaboration.md).
- **Skills:** canonical workflows live in `.agents/skills/`, which Antigravity, Gemini CLI and Codex discover directly; only Claude Code needs delegating adapters (`.claude/skills/`).
- **License:** original project code/documentation use MIT; dependencies and media retain their own licenses. The inventory and full texts live in `licenses/` and form the app's About license view.
- **Developer access:** Android debug and unsigned Windows builds require no publisher credentials. Tool installation is explicit; JDK/SDK discovery is portable. Android release signing accepts developer-owned secrets without an owner dependency, while preserving existing local configurations.
- **Public repository:** starts at 0.8.5 / code 13 from a reviewed source-only export, without earlier history, secrets or personal recordings. Commit/push authorization is specific to each checkout and task. Forks are supported without a repository-owner allowlist.
- **Effect preview:** short offline-rendered clips use the same native processing as the mix; looping playback, A/B and debounced parameter updates provide audible feedback. Use project audio where available, otherwise the credited CC0 sample. Live monitoring complements the clip preview (see 2026-11-22).
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

- **Calibration:** validate each actual output/input pair acoustically (recommended, not required, from 0.9); accept at most 5 ms spread for internal/wired routes or 25 ms for Bluetooth. Reconnection invalidates Bluetooth calibration. Store the measured correction with each take so recalibration cannot move existing audio.
- **Bluetooth:** music-profile output and internal microphone are the default-quality route. Selecting a headset microphone uses a non-Bluetooth output; recording and music playback on the same headset are not combined. Verify actual opened devices.
- **Bleed:** retain the short reference filter, clipping-aware fitting and conservative local gain tracking. Do not alter source audio, shift takes or apply a noise gate. Measurements and listening evidence have distinct limits.

## 2026-10-29

- **Project model:** immutable 48 kHz PCM16 originals plus typed non-destructive settings and disposable versioned caches. Save each take on stop; deletion requires confirmation. No take lists, clip arrangement, punch-in or loop recording.
- **Transport:** existing tracks play while a new mono track records; preparation may render offline. Practice loops affect playback only. Android continues transport in the background with an explicit stop notification; interruptions save and stop without automatic restart.
- **Mix:** track cleanup/normalization/chain precede gain/envelope/pan; the master chain precedes −14 LUFS normalization (maximum +24 dB) and a −1 dBFS limiter. The metronome is added afterward.
- **Compatibility:** optional fields have defined defaults; semantic changes require format versioning and migration. Golden-file tests preserve existing user projects rather than updating fixtures to hide incompatibilities.
