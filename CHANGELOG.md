# Changelog

User-visible changes by version, newest first. Current behavior is documented in the [wiki](.wiki/README.md).

## 0.8.6 — test build

- Tapping an effect in the library unfolds its description and parameters; ▶ plays with these values and changes are heard in the running loop. Values are not stored.
- Track and master dialogs use the screen width, and expanded effect controls start at the left edge, giving sliders more room on phones.

## 0.8.5 — first public source release

- Source published under the MIT license; third-party notices and full license texts are in `licenses/` and in About → Open-source licenses.
- About and the effect library credit Google's Material Icons and Paul Walker's Airwin Consolidated alongside Airwindows.

## 0.8.4 — test build

- Audition effects in the library and expanded track/master slots. Loop project audio through the chain, hear parameter changes after a short debounce and compare with/without the selected effect. Empty projects use “Short Jazzy Tune No.1” by painted-panda (CC0).

## 0.8.3 — test build

- Per-slot Airwindows loudness matching preserves track balance; disable it for intentional level changes.

## 0.8.2 — test build

- Contains the 0.8.1 feature set; test builds receive distinct patch versions.

## 0.8.1 — test build

- Android two-channel interfaces offer input 1, input 2 or their sum; calibration/tuner use the same choice. Takes remain mono. Two-channel routes require recalibration.
- Filled dB-scaled waveforms improve quiet-track visibility.
- Master card appears below loop controls, before tracks.
- “+ New project” creates an empty project without recording.

## 0.8 — development build

- Ordered track/master chains with up to six bypassable effects and draggable handles.
- My Audio EQ/compressor presets and reverb, plus a searchable 478-effect Airwindows library with local selection and localized descriptions.
- Existing fixed effects migrate to chains without changing their sound; chain projects require format 3 support.
- Desktop calibration settings can open over an existing project.

## 0.7 — development build

- Mono/stereo imports preserve channel counts throughout mixing, playback and export. Stereo uses balance; effects/envelopes handle both channels.
- Windows MP3 decoding retains float precision until final PCM16 storage.
- New takes default to click/bleed cleanup off, while overdub references remain available.
- Mono projects remain readable; recovering stereo from a mono-converted import requires the original file.

## 0.6 — 2026-11-09

- About includes logo, version, contributors, contact, privacy and open-source licenses, with animated notes. Windows F1 opens it.
- Windows title is “My Audio”; version appears in About.

## 0.5 — 2026-11-09

- Shared files retain extensions, including project ZIPs sent through Google Drive; Android import accepts files without extensions.
- Windows restores window size/position, adds guitar tuner and Ogg Opus import, and appends missing save extensions.

## 0.4 — 2026-11-09

- First Windows app: shared project UI, effects/envelopes, calibrated overdubbing, device selection, import by dialog/drop and mix/stem/backup export.
- ZIPs without project.json import audio entries as aligned tracks with filename-derived ordering/names. Invalid archives leave no partial project.
- Android WAV import and progress feedback; imported tracks explicitly disable cleanup without references.

## 0.3 — 2026-11-09

- Project settings add track import, tuner, scrolling notes and independent silent pre-roll (1–30 s).
- Notes support smooth teleprompter scrolling, chord progressions/section annotations, preserved solo/mute highlighting, and an editor above the keyboard without autocorrection.

## 0.2 — 2026-11-05

- Application-written Ogg/Opus container supports playback in WhatsApp.

## 0.1 — 2026-11-05

- First distributed signed Android release: calibrated multitrack speaker/microphone overdubbing, device selection, project cards and confirmed deletion.
- Metronome/count-in and optional click/bleed cleanup; track gain/pan/normalization/EQ/compression/reverb and envelopes; solo/mute/practice loop.
- Stereo mix at −14 LUFS with limiter, background transport, Ogg/Opus sharing, aligned stems, project ZIP backup/import, chord notes and guitar tuner.
