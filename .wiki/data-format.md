# Project data format

The writer selects the lowest required format: **1** for mono projects without chains, **2** when stereo tracks exist, **3** when a track/master carries an effect chain. Existing supported projects remain readable. Newer unsupported versions are rejected rather than misinterpreted. Source audio is never rewritten by a format migration.

## Storage and ownership

```text
projects/<project-id>/
  project.json                 # project and track parameters
  tracks/<track-id>.wav         # original mono recording or mono/stereo import, PCM16 48 kHz
  tracks/<track-id>.ref.wav     # immutable overdub playback reference, mono, without metronome
  tracks/<track-id>.clean-v<N>.wav  # disposable versioned cleanup cache
```

Android stores this under private filesDir; Windows under Documents/My Audio. Each take/reference is immutable after stop. Caches can be regenerated and are excluded from backups. Deleting a track or a recording only removes it from project.json: its files stay while the project is open, so undo can restore it. Closing the project (back to the home screen) and opening it remove track files not referenced by project.json, including deleted and abandoned interrupted takes. Project deletion removes its directory. The undo history itself is never stored. Cleanup caches of earlier versions (`*.clean-v<n>.wav`) are kept, because older app versions still use their own.

Device preferences/calibration are not project data. Android `audio_devices` stores type/address/name, not persistent port IDs; `calibration` keys include the measurement method and device pair. Bluetooth keys include connection identity (boot/port ID). Unsupported hands-free output preferences fall back to system output. The compatibility key `latest` applies only to phone speaker/internal microphone. Windows uses `devices.json`, `calibration.json` and `window.json` alongside projects. `library.json` stores local selection, for example `{ "selected": ["airwindows.ToTape9", "myaudio.eq", "myaudio.reverb"] }`; absent means My Audio EQ and reverb selected. `audio.json` (Android filesDir, Windows next to the projects) stores the global buffer, e.g. `{ "buffer": 4 }` (0 = Automatic, 1 = Short, 4 = Long; other values read as Automatic); calibration keys of a non-automatic buffer end in `|buffer=<n>`. None of these device files travels in a project backup.

`ProjectJson` uses kotlinx.serialization on both platforms. Fields without values are omitted; numeric representations such as 1.0 remain readable by compatibility readers. The parser validates/clamps documented ranges. See [Project.kt](../shared/src/commonMain/kotlin/com/martensening/my_audio/data/Project.kt) for typed defaults.

## Project and track fields

```json
{
  "version": 1,
  "id": 1793307485396,
  "name": "Demo",
  "createdAtMillis": 1793307485396,
  "sampleRate": 48000,
  "nextTrackNumber": 2,
  "metronome": { "enabled": false, "bpm": 120, "volume": 0.15, "duringPlayback": false },
  "tracks": [{
    "id": 1793307503200,
    "name": "Voice",
    "fileName": "1793307503200.wav",
    "offsetFrames": -97020,
    "lengthFrames": 703872,
    "volume": 1.0,
    "muted": false,
    "createdAtMillis": 1793307520000,
    "countInFrames": 96000,
    "recordedMetronome": { "enabled": true, "bpm": 120, "volume": 0.15, "duringPlayback": false },
    "effects": { "removeMetronome": false, "removeBleed": false }
  }]
}
```

| Field | Contract |
|---|---|
| `offsetFrames` | Project frame corresponding to the first file frame: the take's start (start marker) minus count-in and latency, plus `shiftFrames`. It is the effective position every version reads. Recalibration does not change it. |
| `latencyFrames` | Optional (0.11): round trip compensated in `offsetFrames`; written for takes from 0.11 on and when edited. Absent means a take before 0.11, which always started at frame 0: latency = −offset − countIn, at least 0 (imports: 0). |
| `shiftFrames` | Optional (0.11), default 0: the user's offset contained in `offsetFrames`; positive is later. The take's start is `offsetFrames − shiftFrames + countInFrames + latency`. Moving a track also moves its `volumeEnvelope` points. |
| `lengthFrames` | File duration including count-in, in time frames; stereo does not double it. |
| `channels` | Optional, default 1; value 2 requires format 2. WAV contains interleaved stereo PCM16. More than two channels are rejected. |
| `recordedMetronome` | Click settings used during this take; absent means no recorded click. |
| `countInFrames` | Recorded one-bar count-in at file start, before the take's start, or 0. Silent pre-roll is not recorded. |
| `referenceFileName` | Optional immutable playback reference for bleed removal. |
| `effects` | Input cleanup, normalization and effect chain; formats 1/2 also support fixed EQ/compressor/reverb fields. |
| `pan` | Default 0, range −1 to 1. Mono pan; stereo balance attenuates the opposite channel without mixing channels. |
| `solo` | Default false. When any solo exists, precisely those tracks sound, even when also muted. Mute settings remain stored. |
| `volumeEnvelope` | Optional list of `{ "frame": 48000, "gain": 0.5 }` in corrected project frames, gain 0–1 relative to track volume. Linear between points, endpoint gains held outside, including reverb tails. Missing/empty means gain 1. |
| `notes` | Project Unicode text, default empty; preserve whitespace/newlines verbatim, including backups. |
| `notesTextSize` | Project font size, default 18 sp, clamped 12–30. |
| `notesScroll` | Project `{ "enabled": false, "secondsPerLine": 4.0 }`, clamped 0.5–20 s. |
| `preRoll` | Project `{ "enabled": false, "seconds": 5 }`, clamped 1–30 s. No change to track data. |
| `loop` | Project `{ "startFrames": 48000, "endFrames": 240000, "enabled": true }`; optional playback-only range. Invalid ranges are discarded. |

Envelope parsing sorts by frame, removes duplicate/invalid points and clamps gain. The editor writes unique points within the audible track range. These optional fields do not alone require a format-version increment.

Imported tracks have offset zero, no reference/metronome, countInFrames zero and explicit false cleanup switches. They preserve mono/stereo and discard the external source file after conversion. New microphone takes also explicitly store false cleanup switches, but keep an overdub reference. Missing cleanup switches in existing files retain the compatibility default true; do not change that interpretation.

## Effect chains (format 3)

`effects.chain` replaces fixed EQ/compressor/reverb fields; cleanup and normalization remain the input stage. `master.chain` is optional, default empty. Each chain stores at most six slots in signal order; excess entries are truncated on load.

```json
{
  "removeMetronome": false,
  "removeBleed": false,
  "normalize": { "enabled": false, "targetDb": -6.0 },
  "chain": [
    { "type": "myaudio.eq", "enabled": true,
      "eq": { "enabled": true, "lowDb": -3.0, "presenceDb": 3.0, "highDb": 2.0 },
      "compressor": { "enabled": true, "amount": 0.5 } },
    { "type": "myaudio.reverb", "enabled": true, "seconds": 1.8,
      "preDelayMs": 20.0, "damping": 0.4, "mix": 0.25 },
    { "type": "airwindows.ToTape9", "enabled": true, "values": [0.5, 0.5], "levelMatch": true }
  ]
}
```

The example shows the slot shape; the native catalogue defines each Airwindows parameter count/default. Values are clamped to 0–1 and missing parameters use defaults. `enabled` defaults true and bypass retains parameters. `levelMatch` defaults true. Unknown Airwindows names and unknown effect types are preserved but not rendered (see below); up to 0.8.6 an unknown general effect type rejected loading.

Format 1/2 enabled EQ/compressor becomes `myaudio.eq`, preserving a disabled subcomponent's parameters; enabled reverb follows as `myaudio.reverb`. Disabled effects create no slot. Native comparison tests establish sample-identical migration for the supported signal paths. Save the migrated representation only when the project changes; opening alone does not rewrite files.

## Archives and exports

- Backup ZIP: project.json, referenced original WAVs and reference WAVs, no caches. Import assigns a new project ID, accepts only validated expected entries and rejects missing takes or unsupported versions.
- Individual tracks: numbered names in project order, all tracks including muted ones, original mono/stereo channel count, 48 kHz PCM16. Cleanup and latency/count-in alignment apply; all begin at frame zero with common project duration. Creative effects, gain and pan do not apply.
- ZIP without project.json: audio files (`.mp3`, `.ogg`, `.oga`, `.opus`, `.wav`) at arbitrary depth become ordinary imports. Ignore `__MACOSX/` and `._` metadata. Extract each accepted file to a controlled temporary location, never an entry-supplied path. Write project.json only after complete conversion, so a failure leaves no partial project.
- Android share exports use temporary `cacheDir/exports/`, replaced on subsequent export. Preserve extensions for receiving applications.

## Exchange between app versions (from 0.9)

- Fields the app does not know are kept in `UnknownFields` and written back after the known ones: at project, track, record track, effects and slot level, and inside the known sub-objects `metronome`, `recordedMetronome`, `notesScroll`, `preRoll`, `loop`, `master`, `normalize`, `eq` and `compressor`. A sub-object that is no longer written (e.g. a removed loop) takes its unknown fields with it; `master` is still written without a chain when it carries unknown fields. Elements of rebuilt arrays (volume envelope points, Airwindows `values`) keep only their known content.
- Imported tones (0.10): `{"type": "myaudio.nam", "tone": "<sha256>", "name": "…", "inputDb": 0, "outputDb": 0}` (gains ±24 dB, clamped) and `{"type": "myaudio.ir", "tone": "<sha256>", "name": "…"}`; both write `"levelMatch": false` only when switched off. `tone` is the SHA-256 of the file in the tone library (`<root>/tones/<sha256>.nam|wav` with `tones.json` for names and details); `name` is shown when the tone is missing. These are optional slot types within format 3, so 0.9.x opens them as preserved unknown slots. Project backups add the used tone files as `tones/<sha256>.nam|wav`; importing a backup adds them to the tone library under the slot's name. Mix and track exports contain no tones.
- An unknown effect type opens as a `ChainEffect.Unknown` slot: its JSON is kept verbatim, only `enabled` belongs to this version. It is never rendered and shows “Requires a newer version”; it can be bypassed, moved or removed.
- `recordTrack` (project, optional): the empty record track after `tracks`, with `name` (absent = default name for `nextTrackNumber`), `volume`, `muted` (default true), `solo`, `pan`, `effects` and `volumeEnvelope`. Absent means no prepared empty track; the UI shows “+ New track”, while direct recording uses the default or inherited next-take settings. It is not part of `tracks`, because readers up to 0.8.6 require `fileName` there. A chain on it raises the written version to 3 like any other chain. A solo on it counts like any track solo.
- `latencyCalibrated` (track, optional, default true; only `false` is written): marks a take recorded without calibration. Its latency is 0 until the user enters one (`latencyFrames`); the hint shows while it is 0.
- `latencyFrames` and `shiftFrames` (track, 0.11): describe how `offsetFrames` is made up, which stays the effective position, so 0.9/0.10 play a moved track where it belongs. Their click and bleed removal assumes a start at frame 0 and may sound slightly different for takes recorded from a later start marker. The cleanup cache is deleted when a take's latency changes, because bleed removal aligns with it.
- None of these raises the version. `ReleasedReader086` in the desktop tests is the verbatim 0.8.5/0.8.6 reader; `VersionExchangeTest` proves that projects written now open there, and that fields from a newer version survive reading, editing and saving. 0.8.x drops the new fields when it saves.

## Extension rules

Exchange between app versions in both directions is a goal: prefer additive optional fields within the lowest required version, and raise the version only for unavoidable semantic changes, since installed apps reject higher versions. New optional fields need defaults that preserve existing behavior. Semantic changes/renames require a version and migration; never silently reinterpret existing files. Golden-file tests preserve full/minimal format-1 projects: add migrations rather than changing fixtures to hide regressions. Audio samples and reference files remain unchanged by JSON evolution.
