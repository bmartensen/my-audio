# Changelog

User-visible changes by released version, newest first. Intermediate test builds are folded into the next listed version. Current behavior is documented in the [user guide](https://bmartensen.github.io/my-audio/en/guide/) and the [wiki](.wiki/README.md).

## 0.9.18

- The recording controls sit on a plain screen-background area: full width below the tracks in portrait, a full-height rail on the right in landscape. In landscape, Record is centred in the window on Home and in projects; Home also gets the right-side rail, leaving the full height to the project list.
- The record track is optional. After “Delete recording?”, the remaining empty track can be removed with “Delete track?”, which brings back “+ New track”. “+ New track” also works in an empty project, and Record works without a prepared track. This replaces “Reset?”.
- Removing an effect from a track or master chain and resetting a whole volume curve ask for confirmation; deleting a single curve point stays immediate.
- “+ New project” is a borderless add button like “+ New track”.
- Effect library: subtitles no longer repeat the effect name, and row separators span the full width.
- “Calibrate now” aligns to the right edge of its notice; long screen titles end with an ellipsis.
- About links the My Audio privacy policy on GitHub Pages.
- Installers are also available from the GitHub release, together with checksums and the source of the bundled Java runtime.

## 0.9.8

- Record track: new tracks can be prepared with effects and settings before recording. “Delete recording?” on the last take keeps its settings for a retake. New tracks start muted and are unmuted when a take is saved; an empty record track shows “Empty”, or “Monitoring” when unmuted.
- Live effects: an unmuted empty record track monitors the microphone through its effects while recording. The effect library and track effect editors offer “Listen” and “Live” side by side, with with/without comparison. Recordings always stay dry. Loudspeaker and Bluetooth outputs warn before monitoring.
- Settings → Audio buffer: Automatic, Short or Long for calibration, recording and live monitoring; each choice is calibrated separately.
- Recording is possible without calibration; such takes are saved without latency compensation and marked “Recorded without calibration”.
- Projects move freely between app versions: fields written by a newer version are kept when saving, and effects only a newer version knows stay silent in their slot, marked “Requires a newer version”.
- Airwindows effects render identically every time.
- Track cards list their active effects in one line and show the volume envelope over the waveform. Each effect in a chain has a trash icon in its header.
- Shorter dialogs and notices; explanations moved to the new German/English user guide on GitHub Pages.

## 0.8.6

- Tapping an effect in the library unfolds its description and parameters; ▶ plays with these values and changes are heard in the running loop. Values are not stored.
- Track and master dialogs use the screen width, and expanded effect controls start at the left edge, giving sliders more room on phones.

## 0.8.5

- Source published under the MIT license; third-party notices and full license texts are in `licenses/` and in About → Open-source licenses.
- About and the effect library credit Google's Material Icons and Paul Walker's Airwin Consolidated alongside Airwindows.

## 0.8.4

- Android app with calibrated multitrack overdubbing over the built-in speaker and microphone, device selection, metronome with count-in, silent pre-roll and optional click/bleed cleanup.
- Windows app with the same project UI, import by dialog or drop, and mix, stem and backup export.
- Track volume, pan/balance, normalization, volume envelopes, solo/mute and a practice loop. Mono and stereo imports keep their channel count through mixing, playback and export.
- Ordered chains of up to six bypassable effects per track and on the master: My Audio EQ/compressor and reverb plus 478 Airwindows effects in a searchable library, with per-slot loudness matching and looping A/B previews.
- Stereo mix at −14 LUFS with limiter, background playback and recording, Ogg/Opus sharing, aligned WAV stems, project ZIP backup and import, including ZIPs of audio files as aligned tracks.
- Song notes with chord highlighting and teleprompter scrolling, guitar tuner, Android two-channel input selection and dB-scaled waveforms.
- About with version, contributors, contact, privacy and licenses.
