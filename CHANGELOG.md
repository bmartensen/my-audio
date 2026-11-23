# Changelog

User-visible changes by released version, newest first. Intermediate test builds are folded into the next listed version. Current behavior is documented in the [user guide](https://bmartensen.github.io/my-audio/en/guide/) and the [wiki](.wiki/README.md).

## 0.10.4

- Importing audio files and stems on Android is several times faster.
- Windows: a right-click on a track or the master opens its options, in addition to the long press.

## 0.10.3

- Amp models and cabinets: import Neural Amp Modeler profiles (.nam) and cabinet impulse responses (.wav) you downloaded, for example from TONE3000, also as ZIP. They work like every other effect, live and in the mix, with **Input**, **Output** and **Match level**.
- Project backups contain the amp models and cabinets they use.
- **SoundCraft session:** a fourth share option exports the project with tracks at their positions, levels, pan, volume envelope and effects for the desktop DAW SoundCraft. **Import project** opens such a session again, with every track at its position.
- Android's backup and transfer to a new phone include projects and the effect library; calibration stays on the device.

## 0.9.18

- The record track is optional: delete the empty track and add one again with **New track**, also in an empty project. **Record** works without a prepared track as well.
- Removing an effect and resetting a volume envelope ask for confirmation.
- **About** links the privacy policy.
- UI improvements in landscape, on the home screen and in the effect library.

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
