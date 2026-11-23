---
layout: default
lang: en
title: Guide
description: The My Audio guide – every screen and function explained.
permalink: /en/guide/
---

# Guide

My Audio is a studio on your phone. Capture an idea, record track by track, add effects and share the result. This guide explains every screen. The screenshots show the German interface. On Windows everything looks the same and is operated with the mouse.

Guide version: My Audio {{ site.release }}. Available installers are listed on the [download page]({{ '/en/#download' | relative_url }}).

<nav class="toc" markdown="1">
- [Getting started](#getting-started)
- [Home screen](#home-screen)
- [Project view](#project-view)
- [Tracks](#tracks)
- [The record track](#the-record-track)
- [Recording and playback](#recording-and-playback)
- [Track settings](#track-settings)
- [Effects: reverb example](#effects-reverb-example)
- [EQ and Airwindows](#eq-and-airwindows)
- [Live monitoring](#live-monitoring)
- [Master](#master)
- [Volume envelope](#volume-envelope)
- [Notes and chords](#notes-and-chords)
- [Metronome](#metronome)
- [Project settings](#project-settings)
- [Tuner](#tuner)
- [Sharing and backups](#sharing-and-backups)
- [SoundCraft](#soundcraft)
- [Effect library](#effect-library)
- [Amp models and cabinets](#amp-models-and-cabinets)
- [Settings](#settings)
- [Latency calibration](#latency-calibration)
- [Windows](#windows)
- [Data and privacy](#data-and-privacy)
</nav>

## Getting started

1. **Install:** see [Download]({{ '/en/#download' | relative_url }}).
2. **Calibrate:** open **Settings → Latency calibration** and tap **Start calibration**. This keeps new recordings in time with the existing tracks. You can record without calibrating. Tracks may then be slightly delayed.
3. **Record:** tap the red record button on the home screen. My Audio creates a new project and starts the first take right away.

Headphones are always the best choice when overdubbing or monitoring.

## Home screen

<img class="shot" src="{{ '/images/home.png' | relative_url }}" alt="Home screen with projects, New project and record button">

- **Project card:** tap to open the project. Below it you see the number of tracks and the length. The pencil renames the project. The trash deletes it, including all recordings.
- **Record button (red):** creates a project named after the date and time and starts the first recording.
- **New project** below the list creates an empty project without recording.
- **Effect library** (wave icon): all effects for browsing and selecting, see [Effect library](#effect-library).
- **Import project** (down arrow): opens a ZIP file. A backup from **Share → Back up project** is restored as a project. A zipped SoundCraft session becomes a project with every track at its position. A ZIP with audio files (MP3, Ogg, WAV) becomes a new project with one track per file. A leading number in the file name ("01 Drums.wav") determines the order.
- **Settings** (gear): devices, audio buffer, calibration and info.

## Project view

<img class="shot" src="{{ '/images/project.png' | relative_url }}" alt="Project view">

At the top are **Back**, the project name, **Notes**, **Share** and **Project settings**. Below that:

- **Calibration notice**, if the selected devices have not been calibrated yet.
- **Practice loop:** drag a region on the bar. A short tap removes the loop. The button on the left enables or disables the loop. The text shows its range. When active, **Play** starts at the loop start and repeats the region seamlessly.
- **Master:** the effects on the whole mix. Tap to open it (see [Master](#master)).
- **Tracks** and at the bottom the **record track**.
- **Transport bar:** time, play, record and metronome.

<img class="shot landscape" src="{{ '/images/project-landscape.png' | relative_url }}" alt="Landscape project with the record button centred vertically">

## Tracks

<img class="shot" src="{{ '/images/project-record-track.png' | relative_url }}" alt="Tracks with effect row and volume envelope">

Each card shows a recording on the shared timeline:

- **Name and length**, followed by the volume when it differs from 0 dB.
- **Volume envelope** (curve icon, coloured when an envelope is set): opens the [editor](#volume-envelope).
- **S – Solo:** when any track is soloed, only the soloed tracks are heard.
- **Speaker – Mute:** mutes the track. Solo takes priority.
- **Trash:** deletes the track.
- **Effect row:** the enabled effects in order.
- **Recorded without calibration:** this track was recorded without latency compensation and may sit slightly behind the others.

A long press on the card opens the [track settings](#track-settings). During playback, tapping a waveform jumps to that position.

## The record track

**New track** below the tracks prepares an empty record track for the next take, also in a project without recordings. It shows **Empty** and lets you set up the instrument before recording. The next take goes into this track.

- **Effects** (wave icon) or long press opens its settings: name, effects, volume and panning.
- **Mute** on the empty record track means the microphone will not be monitored while recording. Unmuting it shows **Monitoring** on the card, and you hear yourself through its effects while recording (see [Live monitoring](#live-monitoring)).

You can also press **Record** without preparing a track. The new track then takes over the effects and input settings of the last recording.

## Recording and playback

- **Play** starts at the beginning or at the start of the active loop. Tap again to stop.
- **Record** starts a take. All audible tracks play along while the new track is recorded. If a **pre-roll** is set, a silent countdown runs first. If the metronome is on, it counts one bar in.
- **Metronome** opens the [metronome settings](#metronome).
- With a USB interface with two inputs, the choice **Input 1**, **Input 2** or **Input 1 and 2 mixed** appears below the time. Recordings are always mono.

Recordings are always saved without effects. Effects, volume and envelope only apply during playback and sharing – you can change them any time. On Android, playback and recording continue in the background with a notification, even with the screen off.

## Track settings

<img class="shot" src="{{ '/images/track-settings.png' | relative_url }}" alt="Track settings">

A long press on a track opens its settings in signal order. The **pencil** renames the track. **Done** applies, **Cancel** discards all changes.

**Input**
- **Remove metronome** – only for takes recorded with an audible click: removes the recorded click.
- **Remove bleed** – only for takes recorded over loudspeaker playback: removes the other tracks that the speaker bled into the recording.
- **Normalize** – brings the peak to a target level.

**Effects** – up to six, processed top to bottom:
- **Add effect** offers the effects selected in the [library](#effect-library).
- **Handle on the left** (two lines): drag to reorder.
- **Tick:** disables the effect without losing its settings.
- **Trash:** removes the effect.
- Tap the **name** to expand the effect and its controls.

**Channel**
- **Volume** from silent to +6 dB.
- **Pan** (for stereo imports **Balance**) from L 100 to R 100.

## Effects: reverb example

<img class="shot" src="{{ '/images/effect-reverb.png' | relative_url }}" alt="Reverb with controls">

**Reverb (My Audio)** places the track in a room, from a small chamber to a cathedral.

| Control | Effect |
|---|---|
| **Room** | Preset **Chamber**, **Room**, **Hall**, **Church** or **Cathedral**. |
| **Decay time** | 0.2 to 10 s – how long the reverb takes to die away (RT60). Larger values sound like a bigger room. |
| **Pre-delay** | 0 to 200 ms pause before the first echo. A little pre-delay keeps vocals and instruments upfront and clear. |
| **High damping** | 0 % like stone (highs linger) to 100 % like a soft room (highs die away quickly). |
| **Mix** | 0 % dry, 50 % equal parts room and direct signal, 100 % reverb only. |

The reverb works in stereo. A mono track becomes spatial.

### Auditioning and comparing

<img class="shot wide" src="{{ '/images/effect-preview.png' | relative_url }}" alt="Auditioning with with/without switch">

- **Listen** plays a roughly six-second excerpt in a loop.
- **With / Without** compares the chain with and without this effect.
- **Live** sends your microphone through the effects instead, see [Live monitoring](#live-monitoring).

## EQ and Airwindows

**EQ (My Audio)** combines an equalizer and a compressor:
- **Sound:** presets **Vocal**, **Guitar**, **Piano** and **Neutral**.
- **Equalizer:** **Bass** (150 Hz), **Presence** (3 kHz) and **Treble** (8 kHz), ±12 dB each, with their own switch.
- **Compressor:** a single **Strength** control (0–100 %) sets threshold and ratio together. The volume is compensated automatically.

<img class="shot" src="{{ '/images/effect-airwindows.png' | relative_url }}" alt="Airwindows effect ToTape9 with controls">

**Airwindows** are 478 effects by Chris Johnson – from tape machine to distortion to reverb. Each has its own controls with its own units, a short description and:
- **Defaults** resets all controls.
- **Level matching** keeps the loudness the same when the effect is enabled.

## Live monitoring

My Audio can send your microphone through effects in real time while you play or sing:

1. **While recording:** unmute the empty record track. During recording you hear yourself through its effects, together with the other tracks.
2. **While experimenting:** in the library and in the track settings, **Live** sends the microphone directly through the effect to the output – without playback and without recording.

<img class="shot dialog" src="{{ '/images/live-warning.png' | relative_url }}" alt="Confirmation before monitoring over Bluetooth">

- Use headphones. The built-in speaker can cause feedback (squealing). Bluetooth adds a noticeable delay.
- The recording stays dry. The raw microphone signal is always saved. The effects are applied afterwards exactly as heard while monitoring.
- The monitoring delay equals the calibrated latency. The [audio buffer](#settings) can shorten it.

## Master

<img class="shot dialog" src="{{ '/images/master.png' | relative_url }}" alt="Master effects">

The master processes the entire mix – with the same effect chain as a track, but without input and channel. After the chain a fixed output stage always follows: loudness to −14 LUFS and a limiter at −1 dBFS. This way shared mixes sound equally loud.

## Volume envelope

<img class="shot" src="{{ '/images/envelope.png' | relative_url }}" alt="Volume envelope editor">

The curve icon on a track lets you draw a fade-in, fade-out or quieter passages:

- Tap in the **waveform** to place a point, drag to move it. The height ranges from 0 % (bottom) to 100 % (top) of the track volume.
- The **trash** removes the selected point. **Reset to full volume** removes all points.
- The **overview** below the waveform shows the full track: tap to centre, drag to scroll. The **magnifiers** zoom from 1× to 8×.
- **Solo preview** (▶) plays the track with the new envelope and its effects. While playing, tap the waveform to jump.
- **Save** applies the envelope.

## Notes and chords

<img class="shot" src="{{ '/images/notes.png' | relative_url }}" alt="Notes with highlighted chords">

The notes icon at the top shows the project's lyrics in place of the tracks.

- Chords are highlighted automatically – German and English names, extensions and slash chords like *Am/G*.
- **Magnifiers** change the font size, the **pencil** opens the editor, the **X** shows the tracks again.
- With **Scroll notes during playback** in the [project settings](#project-settings) the text scrolls like a teleprompter while playing.

## Metronome

<img class="shot dialog" src="{{ '/images/metronome.png' | relative_url }}" alt="Metronome settings">

- **Metronome on:** the click is audible during recording and counts one bar in.
- **Also during playback:** click also while playing.
- **Tempo:** 40 to 240 BPM with − / + or slider.
- **Volume** of the click.

A recorded click can be removed later with **Remove metronome** in the track settings.

## Project settings

<img class="shot dialog" src="{{ '/images/project-settings.png' | relative_url }}" alt="Project settings">

- **Import track:** an MP3, Ogg or WAV file as a new track from the project start (mono or stereo).
- **Guitar tuner:** see [Tuner](#tuner).
- **Scroll notes during playback:** playback and recording start at the top of the notes. After four lines the text scrolls further, at 0.5 to 20 s per line.
- **Pre-roll before recording:** a silent countdown of 1 to 30 s, so you can get to your instrument.

## Tuner

<img class="shot dialog" src="{{ '/images/tuner.png' | relative_url }}" alt="Guitar tuner">

Play an open string. The tuner detects the string automatically (standard tuning E–A–D–G–B–E, A = 440 Hz) or you tap a string. The scale shows the deviation in cents. Within ±5 cents it reads **In tune**.

## Sharing and backups

<img class="shot wide" src="{{ '/images/share.png' | relative_url }}" alt="Share menu">

- **Share mix:** exactly what **Play** lets you hear, as stereo Ogg/Opus – small enough for messengers.
- **Stems:** each track as a cleaned WAV file from the project start, without effects, volume or panning, in a ZIP – for further work in a DAW.
- **Back up project:** the whole project with all original recordings and settings as a ZIP. **Import project** on the home screen restores it, also on a different device.
- **SoundCraft session:** the project as a session for the desktop DAW SoundCraft, as a ZIP (see [SoundCraft](#soundcraft)).

## SoundCraft

[SoundCraft](https://github.com/storytold/soundcraft) is an open-source DAW for the PC. Use it to move and cut tracks you recorded on the go, then take the session back to your phone.

- **Share → SoundCraft session** creates a ZIP. Unzip it on the PC and open the *.scraft* file in SoundCraft.
- Every track sits at its place in the song there, with volume, panning, mute and volume envelope.
- **EQ (My Audio)** with **Compressor** and **Reverb (My Audio)** become the matching SoundCraft effects and stay adjustable. They sound similar there, but not exactly the same.
- Airwindows effects, amp models and cabinets do not exist in SoundCraft. They are rendered into the track, and the untouched take is next to it in the playlist “Dry”.
- Back to the phone: save the session in SoundCraft, zip its folder and open it with **Import project**. Every track keeps its position, also when you moved clips there. Then record more, add effects and export the session again.
- What My Audio cannot take over, such as other plugins or MIDI tracks, is listed in the project's **Notes** afterwards.

When you move to a new Android phone, Android's data transfer takes your projects and the effect library along. Calibrate again on the new phone.

## Effect library

<div class="shots">
  <img class="shot" src="{{ '/images/library.png' | relative_url }}" alt="Effect library">
  <img class="shot" src="{{ '/images/library-preview.png' | relative_url }}" alt="Auditioning in the library">
</div>

The library contains **EQ (My Audio)**, **Reverb (My Audio)**, your imported amp models and cabinets and 478 Airwindows effects.

- **Search** by name or description, **filter** by **All**, **Selected** or category.
- Tap an **effect** to expand it with description and controls. **▶** auditions it right away. When expanded, it offers **Listen** and **Live** with **With / Without**.
- The **tick** selects an effect for **Add effect** in the tracks.

## Amp models and cabinets

An amp model is a profile of a real amplifier or pedal, learned with the [Neural Amp Modeler](https://www.neuralampmodeler.com). It sounds like the device itself. A cabinet impulse response (IR) recreates the speaker and the microphone in front of it. Together they make a complete guitar or bass sound. Many profiles already contain the cabinet, then you need no IR.

**Amp models and cabinets** in the library brings such profiles (*.nam*) and impulse responses (*.wav*) into the app, for example from [TONE3000](https://www.tone3000.com). Download them in your browser and tap **Import**. You can pick a downloaded ZIP file directly, the app takes every profile and impulse response from it.

- Imported tones are selected right away and offered under **Add effect**, live and during playback.
- An amp model has **Input** and **Output**, both kinds have **Match level**.
- **Remove tone** deletes it from this device. Project backups contain the tones they use.
- Respect the license shown on each tone's page when you publish recordings made with it.

## Settings

<img class="shot" src="{{ '/images/settings.png' | relative_url }}" alt="Settings">

- **Playback device** and **Microphone:** all connected devices. A Bluetooth headset microphone records in phone quality and plays through the phone speaker.
- **Audio buffer:** how much audio the input and output buffer.
  - **Automatic** uses the system default for low latency. The best choice for most devices.
  - **Short** reduces the delay during live monitoring, but may cause clicks and dropouts.
  - **Long** is the most robust when you hear clicks – with slightly more delay.
- **Latency calibration:** shows the measured latency and opens the calibration.

## Latency calibration

<img class="shot" src="{{ '/images/calibration.png' | relative_url }}" alt="Calibration with result">

Every device needs some time for a sound to leave the speaker and arrive back at the microphone. The calibration plays short sweeps, records them and measures this round trip. This lets My Audio place each new recording precisely on the existing tracks.

- Place the phone on a flat surface, make sure it is quiet and set the media volume to roughly two thirds. Tap **Start calibration** or **Recalibrate**.
- The result shows **Latency**, **Stability** and **Signal**.
- Each combination of playback device, microphone and audio buffer is calibrated separately. Bluetooth needs to be recalibrated after reconnecting.

## Windows

The Windows version has the same functions and layout, operated with mouse and keyboard:
- A right-click on a track or the **Master** opens its settings, like a long press.
- Devices, audio buffer and calibration are in the settings dialog (gear).
- Projects live in *Documents/My Audio*. ZIP files can also be imported by drag and drop. Save and share open a save dialog.
- **F1** opens **About My Audio**.
- Windows cannot distinguish speakers from headphones. Pay attention to headphones yourself when monitoring live, to avoid feedback.

## Data and privacy

My Audio needs no account and no internet service. Projects stay on your device (Android: private app storage, Windows: *Documents/My Audio*). What you share is your choice through **Share**. Backups contain all original recordings. Details are in the [privacy policy]({{ '/en/privacy/' | relative_url }}).

---

Questions, ideas or bugs? The source code and version history are on [GitHub]({{ site.repository_url }}).
