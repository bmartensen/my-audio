# Product requirements

Agreed current behavior for My Audio 0.9.0. Implementation acceptance is tracked separately in [current work](current-work.md) and [backlog](backlog.md). Proposals below are not commitments.

## Purpose, identity and platforms

My Audio is a studio from the smartphone: capture musical ideas, add overdubs and shape demos with effects. It does not aim to compete with professional DAWs. Windows complements the same direct recording workflow with a larger screen and mouse precision. Android/Windows versions stay synchronized; Linux is a later target, macOS undecided.

The interface stays brief: settings and dialogs carry labels and at most one short line, never explanatory paragraphs; confirmations name what happens in one line. Explanations of what an option does live in the user guide (`docs/`, published with GitHub Pages).

Application ID: `com.martensening.my_audio`. Branding combines a graphite/nickel microphone, black piano lacquer and ivory keys, with a small red recording light. Assets and localized store copy are in `branding/`. The interface supports English/German, light/dark themes and Android dynamic colors. Both portrait and landscape work. Core functionality is local, with no account or required network service. Projects use private app storage on Android and Documents/My Audio on Windows. Android API 29 is the provisional minimum. Pixel 9 is a reference test device; emulator timing is not an audio acceptance test.

## Home and project lifecycle

- Home lists project cards with open, rename and delete actions. Project rename belongs here, not in the project view.
- The large bottom-centered Record button creates a date/time-named project and starts recording after microphone permission, also without calibration. “+ New project” creates an empty project without recording, including on an empty home screen; permission is deferred until the first take.
- Each stopped take is immediately saved as a track. Delete failed takes through the track delete action. Both project and track deletion require confirmation.
- Existing tracks play during a new recording. Record starts from project frame zero and toggles to stop; repeated recording adds tracks. Play starts at zero unless the practice loop is active. Tracks remain fixed on the timeline.

## Devices and calibration

Settings provide separate output and microphone choices, saved immediately. All connected adapter types are listed. Unavailable choices visibly fall back to system output/internal microphone. Android defaults to the internal microphone and music-quality output; system volume buttons control media volume, with no separate master-volume control.

Bluetooth output uses the music profile, not narrow-band hands-free playback. Selecting a headset microphone switches to the phone speaker/non-Bluetooth output; selecting Bluetooth music output switches the microphone to internal. Verify the actual opened route. Each new output/input pair should be calibrated before recording; an uncalibrated pair shows a banner, but recording stays possible (see record track below). Playback does not require calibration. The dedicated calibration view shows the devices, latency, stability and a green OK/red error check. Maximum accepted spread is 5 ms for wired/internal routes and 25 ms with Bluetooth. Bluetooth gets one second of silent warmup, and reconnection requires calibration again. Known non-Bluetooth pairs retain measurements. Stored takes retain their original offset after route changes.

Built-in speaker/microphone overdubbing is supported. For Android two-channel USB input, the project shows input 1, input 2 or 1+2 below the clock, defaulting to the sum and remembering the device choice. Calibration and tuner use the same input. Takes remain mono, centered in the stereo mix. Single-channel/internal devices hide this selector; while recording, a level meter occupies the same row to avoid layout jumps.

## Project view and transport

- Record is bottom-centered, with Play on the left once tracks exist and metronome on the right. Landscape places clock, level, Record and equal-size Play/metronome controls in a right-side column, with 32 dp content spacing.
- Live dBFS meter: green through −12 dB, yellow through −3 dB, then red, with peak hold. A live recording waveform is recomputed from the saved file after stopping.
- Track waveforms share the project timeline. Mute and multiple solo selections persist. If any track is solo, only solo tracks sound (including a solo track also marked muted); mute state remains stored. This applies to playback, overdub reference and mix export. Active buttons retain their primary-color filled state while transport disables editing; inactive controls fade.
- Drag the timeline to select a practice loop of at least 0.5 s; a short tap clears it. The loop button toggles the stored range. Text distinguishes active, disabled and absent ranges. Active playback starts at the loop and wraps sample-accurately; Record never loops.
- A track card shows its switched-on effects in one line below the name, comma-separated and cut with “…”; a track with a volume envelope shows the curve as a subtle line over its waveform (top 100 %, bottom 0 %).
- During playback, tapping any waveform seeks. Long press opens track volume/effects; the pencil renames a track. The master card follows calibration notice and loop controls, before the tracks.
- The top bar contains Back, project name, Notes, Share and Settings. Track import replaces the settings icon with progress. Project settings offer import and tuner when stopped, plus notes scrolling and pre-roll, saved immediately per project.
- Android Play/Record continue in the background and with the screen off. An ongoing notification opens the project or stops/saves the take. Audio interruptions stop/save without automatic restart.

## Audio import and sharing

Import MP3, Ogg Vorbis/Opus and WAV as a new track at frame zero. Convert to 48 kHz PCM16, preserving one/two channels; reject unsupported files and channel counts. Track names come from filenames. Imports have no recording latency, count-in, metronome reference or bleed reference, and explicitly disable both cleanup switches. They otherwise support ordinary track processing.

Share offers: the audible stereo mix as Ogg/Opus at 48 kHz; all individual tracks (including muted tracks) as aligned cleaned mono/stereo WAVs in a ZIP, with no creative effects/gain/pan; or a complete project ZIP backup. Android opens the share sheet, Windows a save dialog. File extensions are retained or added when omitted.

Home imports ZIPs as new projects. With project.json, validate and restore a backup. Without it, each supported audio entry becomes a track at frame zero. Ignore unrelated entries and platform metadata; reject unreadable audio without leaving a partial project. Project name is the ZIP basename. Track names omit a leading one/two-digit number, and ordering follows that number then name. Audio in subdirectories is supported. Never use archive entry paths as destination paths. [Data format](data-format.md) specifies persistence and compatibility.

## Mixing and effects

Source audio is immutable; settings are non-destructive. Preparing the mix offline before transport is acceptable. Effects also process existing tracks during overdubs; a newly captured track receives its effects after stop. New takes default to cleanup off, while overdub references remain available for later use. Bleed removal uses exactly the playback recorded during that take, not today's mix.

Signal order: click removal → bleed removal → normalization → up to six ordered effect slots → gain/envelope/pan. Summed tracks pass through up to six master slots, then fixed −14 LUFS normalization (maximum +24 dB gain) and a −1 dBFS limiter. The metronome is added afterward.

| Control | Range / behavior |
|---|---|
| Normalization | Peak target −24 to 0 dBFS; default −6 dBFS |
| EQ | Low shelf 150 Hz, presence bell 3 kHz, high shelf 8 kHz; each −12 to +12 dB |
| Compressor | Strength 0–100%, jointly mapping threshold −10 to −30 dBFS and ratio 1.5:1 to 5:1, automatic makeup gain |
| Reverb | Stereo, RT60 0.2–10 s, pre-delay 0–200 ms, damping and mix; chamber/room/hall/church/cathedral presets remain editable |
| Tone presets | Voice, guitar, piano and neutral configure EQ/compressor together |
| Pan/balance | L100–center–R100, center snapping. Mono pan; stereo balance attenuates the opposite channel without cross-mixing |

Stereo has one track card and a waveform showing the maximum channel magnitude per time bucket. Normalization and compression link channel gain; EQ/reverb preserve channel paths. Mix, playback and mix export are stereo.

The library exposes 478 embedded Airwindows effects plus My Audio EQ/compressor and reverb, with search, categories and localized descriptions. Local selection controls the add-effect menu; only the library adds new choices. Existing project slots remain usable when deselected from the library. Slots reorder by handle, bypass without losing parameters, are removed by the trash icon in their header (always visible, also folded) and expand one editor at a time. Track and master dialogs use the screen width with 16 dp side margins (at most 560 dp), and an expanded slot's controls span the full dialog width, starting below the drag handle. Track dialog sections are input, effects and channel. Master has the same chain without cleanup, normalization or channel controls. Airwindows loudness matching defaults on (BS.1770, maximum ±24 dB), with per-slot opt-out for intentional level changes. No per-track reverb sends.

## Effect audition

Library effects can be auditioned before selection. Tapping an entry unfolds its full description and parameters, one entry at a time, starting from the defaults; ▶ plays with the current values and changes are heard in the running loop. Values are discarded when the entry folds, so they never change the library selection or projects. Expanded track/master slots offer looping playback through the complete chain and A/B with/without that slot. Use roughly six seconds from the track or pre-master mix, preferring the loop range, otherwise playback position. Empty projects/library use the credited 7.2 s CC0 “Short Jazzy Tune No.1” clip by painted-panda (48 kHz PCM16 stereo, about −18.2 LUFS, peak −1.4 dBFS).

Parameter changes rerender after roughly 150 ms idle, preserving playback position; level matching applies. Preview does not commit project changes. The native offline path includes reverb tails, and a separate player loops the result. Android performance and selected-device routing remain explicit acceptance work. Live input monitoring is described under the record track and live effects.

## Planned for 0.9: record track and live effects

Agreed on 2026-11-22 and implemented in 0.9.0; hardware acceptance is open. A reset record track takes the default name for the next track number, which keeps counting. A solo on the empty record track counts like any track solo.

**Record track.** The last track is always the record track, shown even when empty; a new project starts with one empty track. Record captures into it when empty. When it already holds a take, Record appends a new record track that inherits the effect chain and input settings (not name, envelope, mute or solo; it starts muted) and records there. “+ empty track” appends an empty record track to prepare another instrument. Imports are inserted before an empty record track. An empty record track is excluded from mix, stems and the overdub reference.

**Deleting.** On the record track the trash is two-stage. With a take: “Delete recording?” removes the audio and its reference but keeps all settings. Without a take but with changed settings: “Reset?” restores the state of a newly created track: default name, effects, volume, pan, envelope, muted and not solo. Empty and at defaults: the trash is disabled. Older tracks keep whole-track deletion with one confirmation.

**Recording without calibration.** Record is always available when the device can capture, on Home (Android and Windows) and in the project, also after a route change during pre-roll. An uncalibrated route keeps the calibration banner and shows a non-blocking warning when a take starts; it no longer redirects to calibration. Such a take stores no latency compensation (its offset accounts only for the count-in) and is marked as uncalibrated; its track card shows a small hint. Later calibration does not move it.

**Live monitoring while recording.** The record track's Mute switch controls monitoring. An empty record track starts muted, so nothing is monitored. Unmuting it monitors the microphone: dry without effects, otherwise through its effect chain in the real-time callback, heard together with the playback. After a take is saved, the track is unmuted automatically. Every newly created track starts muted, including one appended by Record or “+ empty track”. “Delete recording?” keeps the current Mute state with the other settings until it is changed or “Reset?” restores muted. Mute is an ordinary setting for the trash state: an empty, unmuted record track can be reset. Unmuting on the built-in loudspeaker or a Bluetooth output shows the feedback/delay warning. The saved take stays dry; after stop the identical chain is rendered offline. Normalization and click/bleed cleanup remain offline-only; level matching uses a fixed live estimate, so the live level may differ slightly from the rendered result.

**Live audition.** Library entries and track slot editors offer a Live switch next to ▶. Live sends only the selected microphone (respecting USB input selection) through the effect or track chain to the output, with no project playback or metronome; parameter changes apply immediately. The clip preview remains, and is the only option for master chains. Before Live starts on the built-in loudspeaker or a Bluetooth output, a warning explains feedback or delay; the user may continue. A/B compares with and without the effect live as well.

**Audio buffer.** Settings offer one global choice for calibration, takes and live monitoring as a compact Automatic | Short | Long switch without explanation: Automatic is the system's low-latency default and keeps existing calibrations, Short and Long use 1 or 4 device periods (desktop periods of 128 frames). A medium step is omitted because it matches Automatic on Android. Each choice is calibrated separately, so changing it shows the calibration notice until measured; a stored choice this version does not offer falls back to Automatic.

## Volume envelope editor

A curve icon opens a dedicated per-track editor. Add/drag points in the waveform; points cannot cross neighbors and long press cannot capture a different point. Gain is 0–100% relative to track volume, with delete/reset/save actions. Existing-point capture radius is 48 dp. No additional position/gain sliders or explanatory text above the plot.

The overview below the waveform shows the full track and visible range; tap to center, drag to scroll. Magnifiers halve/double zoom from 1× to 8×, keeping the selected point/playhead visible. Landscape puts playback/actions beside the card. Solo preview uses the draft envelope with track effects/gain; waveform taps seek while playing and point editing is disabled. Playback ends on stop/save/exit/end. Back with unsaved edits asks Save/Discard/Cancel; selection, zoom and seek alone do not mark the draft dirty.

## Metronome, notes and tuner

Metronome defaults off, 120 BPM, 15% level and recording-only; optional playback use is persisted. When enabled, Record captures one four-beat count-in with negative time, then starts the project. Recorded isolated clicks support optional template removal. Silent pre-roll is separate: off by default, 5 s default, range 1–30 s. Prepare calibration/mix first, then show a red countdown and progress card; no capture/playback occurs during pre-roll. Stop/notification stop/exit cancels without a take. Metronome count-in follows it.

Each project has one verbatim Unicode notes field. Notes replace the track area while keeping transport accessible, toggle with one tap, preserve scroll position and keep the screen awake. Editing supports save/cancel, no placeholder text, no keyboard correction/suggestions/capitalization/learning, and remains above the keyboard. Lines never wrap; horizontal scrolling preserves chord alignment. Font size 12–30 sp in steps of 2 (default 18) is project-specific.

Chord highlighting recognizes English/German names, extensions, slash chords, parentheses and progressions. Lowercase minor names require chord-line context rather than highlighting ordinary lyric words. Bar/repeat markers, arrows, section labels and parenthesized annotations are supported; tablature is excluded. Parser tests define edge cases such as Em/D versus Em/D/C and D/F#m7.

Optional teleprompter scroll defaults off, 4 s/line, configurable 0.5–20 s in 0.5 s steps. Start Play/Record at the top; Record includes pre-roll but the four-line initial pause starts at project frame zero. Scroll smoothly per frame after that pause, allow manual adjustment, preserve position on stop, and catch up if notes open during transport. Speed uses rendered line height.

The tuner uses the selected microphone without calibration, only with transport stopped. Standard guitar tuning is E2–A2–D3–G3–B3–E4 at A4=440 Hz, with automatic/manual string selection, frequency/cents, independent live input meter and stable layout. ±5 cents shows green in-tune status. The scale switches between ±50/±300 cents with hysteresis. Closing, backgrounding or interruption releases the microphone.

## Windows specifics

Use the same UI layout and feature scope, with mouse editing, native file dialogs and drag/drop project import. First window is centered at 1280×800 (16:10); later size/position are restored if visible on a connected screen. Output/input choices are separate, with visible fallback for disconnected devices and per-pair calibration. Distribution uses a per-user MSI with Start-menu/desktop shortcuts and preserves projects on uninstall. Android-only OS features include the notification service, Bluetooth routing and hardware media-volume keys.

## Explicit scope boundaries and open decisions

No punch-in, loop recording, stereo microphone recording, take lists, cloud storage, markers, plugin hosting (VST3/CLAP), TONE3000, NAM/IR import or reverb sends are agreed. Track fades use envelopes instead of a dedicated trim tool. Manual timing nudge remains a proposal. Minimum Android version and future platform expansion remain open decisions.
