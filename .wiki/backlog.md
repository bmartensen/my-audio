# Open work

Only outstanding work belongs here. Agreed scope is in [requirements](app-requirements.md); evidence and limitations are in [current work](current-work.md).

## Version 0.9: record track and live effects

Agreed scope: [requirements](app-requirements.md#record-track-and-live-effects).

- Show a project-level hint when a project contains effects that require a newer version.
- Check the optional record track (delete recording, delete empty track, + New track) and recording without calibration on Windows and on the Pixel 9 (emulator checked), including imports while an empty record track exists.
- Accept live monitoring on the Pixel 9 (internal, wired, USB interface) and Windows: heard latency per buffer setting (Automatic/Short/Long, each calibrated), dropouts, callback CPU with six heavy Airwindows slots, Live in library and track dialogs, monitoring while recording, and that takes stay dry.
- Decide from those measurements whether the buffer default should change and whether xruns during live audition need a visible notice; recording already stops on dropouts.
- Stop live audition when the app goes to the background; it currently runs until the dialog or library closes.
- Desktop cannot tell loudspeakers from headphones, so Windows shows no feedback warning; only the Live hint asks for headphones.

## Product and hardware acceptance

- Confirm the provisional minimum Android version (API 29).
- Add selected real product screenshots to the store assets under `branding/`.
- Exercise single-track import on a physical Android device with MP3 and a messaging-app voice recording.
- Validate a real multitrack ZIP on Android for duration, names and alignment; backup/export has been exercised, full acceptance remains open.
- Use 0.8.6 in real Android recording sessions, including overdubbing and sharing project ZIPs through Google Drive.
- Establish Microsoft Store submission access and choose a signed MSI/MSIX distribution route.
- Accept effect preview on Windows and Android: library, track/master dialogs, A/B, parameter changes during looping and empty-project fallback; measure slow six-slot chains on Android (`MyAudioPreview` logs).
- Test stereo preservation and the reported crackle with a fresh import of the original stereo test material (not in the repository) on Windows and Android. See [audio import](audio-import.md).
- Validate Android USB two-channel selection with an interface such as the Behringer UM2: calibration, input 1/2/1+2 levels and tuner on the instrument input.
- Test library interaction on hardware: mouse drag handles, desktop library, Android landscape and preview playback.
- Manually test desktop “Calibrate now” over an open project and immediate recording after closing settings.

## Recording and playback

- Remove obsolete `*.clean-v<old>.wav` cache versions when loading projects.
- Extend `tools/analyze_bleed.py` with native click removal for takes containing a recorded metronome.
- Decide whether to add manual per-track timing adjustment as a fallback; this is a proposal, not agreed product scope.
- Stream long tracks during mixing instead of loading complete tracks into memory.
- Measure long-term drift between Bluetooth music playback and the internal microphone; test additional headsets and Android devices.
- Acoustically calibrate Bluetooth headset microphone plus phone speaker on real hardware, including communication-mode routing during media playback.
- Investigate clipping before raw PCM16 storage: measure capture and playback levels; saved PCM16 alone cannot distinguish hardware clipping from write-time limiting.
- Shorten audio import time on Android. A set of stems that imports within seconds on Windows currently takes about 15 minutes on Android. Measure the time per track first; candidates are the MediaCodec loop in `AudioImport.kt`, which waits up to 10 ms per packet, and per-sample resampling/WAV writing.

## Desktop

- Add input-channel selection if desktop two-channel interfaces need it; capture currently sums channels to mono.
- Unify Android settings/calibration screens with desktop settings when their presentation is aligned.
- Consolidate real-time `MultitrackEngine::mix` and `DesktopEngine::mix` when touching that area.
- Manually test drag/drop and saving without a typed extension; dropping multiple files currently imports only the first.
- Decide full-width versus 720 dp maximum cards for wide windows. Current modifier order fills the available width, including Android landscape.
- Sign the MSI with a developer-owned code-signing certificate; signing alone does not guarantee SmartScreen reputation.
- Install/calibrate the MSI on another Windows computer without development tools.

## Preview and effects

- Measure Airwindows processing cost on physical Android hardware; run `tools/native_tests.ps1` including `airwindows_test`.
- Decide whether preview should use unsaved normalization/cleanup changes in the open track dialog; it currently uses saved input settings until Done.
- Route preview to the app-selected output when it differs from system default: Android AudioTrack preferred-device support and desktop Java Sound mixer selection.

- Library subtitles now start in lower case where the repeated effect name was removed (“ist ein Beschleunigungsbegrenzer …”). Decide whether to capitalize the remainder, rephrase the generated descriptions or show the full text again.
## Development and public repository

- Add instrumented/UI tests for project lists, recording flow and dialogs.
- Exercise missing-tool installation on a clean Windows VM, including license prompts, elevation, interrupted downloads and reboot recovery.
- Invoke the shared skills interactively in Antigravity, Gemini CLI, Codex and Claude Code and confirm discovery and question behavior.
- Provide the Java runtime source for the installers distributed before 0.9.18 (0.8.x–0.9.8 on martensening.com), or document which runtime versions they bundled.
- Project settings still show multi-line explanations (notes scrolling, pre-roll) despite the brief-interface rule; shorten them to one line and move the explanation to the guide.
- Decide Android backup behavior: `allowBackup="true"` without backup rules lets Android include projects in device backups (subject to its size limit). Either exclude recordings with backup rules or document it; the privacy policy currently mentions it.
