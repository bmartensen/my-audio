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

Android stores this under private filesDir; Windows under Documents/My Audio. Each take/reference is immutable after stop. Caches can be regenerated and are excluded from backups. Track deletion removes files with its ID prefix; project deletion removes its directory. Opening a project removes track files not referenced by project.json, including abandoned interrupted takes. Outdated cache-version cleanup remains backlog work.

Device preferences/calibration are not project data. Android `audio_devices` stores type/address/name, not persistent port IDs; `calibration` keys include the measurement method and device pair. Bluetooth keys include connection identity (boot/port ID). Unsupported hands-free output preferences fall back to system output. The compatibility key `latest` applies only to phone speaker/internal microphone. Windows uses `devices.json`, `calibration.json` and `window.json` alongside projects. `library.json` stores local selection, for example `{ "selected": ["airwindows.ToTape9", "myaudio.eq", "myaudio.reverb"] }`; absent means My Audio EQ and reverb selected. None of these device files travels in a project backup.

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
| `offsetFrames` | Project frame corresponding to the first file frame: negative latency plus recorded count-in correction. Recalibration does not change it. |
| `lengthFrames` | File duration including count-in, in time frames; stereo does not double it. |
| `channels` | Optional, default 1; value 2 requires format 2. WAV contains interleaved stereo PCM16. More than two channels are rejected. |
| `recordedMetronome` | Click settings used during this take; absent means no recorded click. |
| `countInFrames` | Recorded one-bar count-in at file start, or 0. Silent pre-roll is not recorded. |
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

The example shows the slot shape; the native catalogue defines each Airwindows parameter count/default. Values are clamped to 0–1 and missing parameters use defaults. `enabled` defaults true and bypass retains parameters. `levelMatch` defaults true. Unknown Airwindows names are preserved but not rendered; an unknown general effect type rejects loading instead of silently discarding processing.

Format 1/2 enabled EQ/compressor becomes `myaudio.eq`, preserving a disabled subcomponent's parameters; enabled reverb follows as `myaudio.reverb`. Disabled effects create no slot. Native comparison tests establish sample-identical migration for the supported signal paths. Save the migrated representation only when the project changes; opening alone does not rewrite files.

## Archives and exports

- Backup ZIP: project.json, referenced original WAVs and reference WAVs, no caches. Import assigns a new project ID, accepts only validated expected entries and rejects missing takes or unsupported versions.
- Individual tracks: numbered names in project order, all tracks including muted ones, original mono/stereo channel count, 48 kHz PCM16. Cleanup and latency/count-in alignment apply; all begin at frame zero with common project duration. Creative effects, gain and pan do not apply.
- ZIP without project.json: audio files (`.mp3`, `.ogg`, `.oga`, `.opus`, `.wav`) at arbitrary depth become ordinary imports. Ignore `__MACOSX/` and `._` metadata. Extract each accepted file to a controlled temporary location, never an entry-supplied path. Write project.json only after complete conversion, so a failure leaves no partial project.
- Android share exports use temporary `cacheDir/exports/`, replaced on subsequent export. Preserve extensions for receiving applications.

## Extension rules

New optional fields need defaults that preserve existing behavior. Semantic changes/renames require a version and migration; never silently reinterpret existing files. Golden-file tests preserve full/minimal format-1 projects: add migrations rather than changing fixtures to hide regressions. Audio samples and reference files remain unchanged by JSON evolution.
