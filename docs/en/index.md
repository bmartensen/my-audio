---
layout: default
lang: en
title: Multitrack recorder for music demos
description: My Audio – record, mix and share track by track on Android and Windows. Free download.
permalink: /en/
---

<section class="hero" id="download">
  <img src="{{ '/assets/icon.png' | relative_url }}" alt="My Audio">
  <h1>My Audio</h1>
  <p class="tagline">Your idea. Your demo.</p>
  <p class="lead">Record track by track, mix with effects and share – on your phone and on your PC. No account, no cloud, free and open source.</p>
  {% include downloads.html %}
</section>

<div class="cards">
  <div class="card"><h3>Record</h3><p>New tracks in time with the existing ones, calibrated to your device.</p></div>
  <div class="card"><h3>Live effects</h3><p>Monitor through reverb, EQ or amp effects while recording – the take stays dry.</p></div>
  <div class="card"><h3>Mix</h3><p>Volume, panning, envelopes and up to six effects per track and on the master, including amp models (NAM) and cabinets (IR).</p></div>
  <div class="card"><h3>Practice</h3><p>Metronome, practice loop, guitar tuner and lyrics with chords.</p></div>
  <div class="card"><h3>Share</h3><p>Mix as Ogg/Opus, stems as WAV, whole projects as ZIP and sessions for SoundCraft – and back again.</p></div>
  <div class="card"><h3>Open</h3><p>MIT licensed, with 478 Airwindows effects. Everything stays local on your device.</p></div>
</div>

<img class="shot" src="{{ '/images/project.png' | relative_url }}" alt="Project view with four tracks, loop and master">

## Installation

**Android:** download the APK and open it. Android asks once whether your browser or file manager may install apps. Install updates the same way over the existing app.

**Windows:** download the MSI and run it. The installer is not signed with a code-signing certificate, so Windows SmartScreen may ask (**More info** → **Run anyway**). My Audio is installed for your user only.

If you like, compare the file's SHA-256 checksum with the one {% if site.release_downloads %}in the [release]({{ site.repository_url }}/releases/tag/v{{ site.release }}){% else %}on the [download page]({{ site.download_page }}?lang=en){% endif %}.

## How it works

The [guide]({{ '/en/guide/' | relative_url }}) explains every screen and function, from the first take to the shared mix. The screenshots show the German interface. Source code, licenses and version history are on [GitHub]({{ site.repository_url }}).
