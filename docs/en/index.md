---
layout: default
lang: en
title: Multitrack recorder for music demos
description: My Audio – record, mix and share track by track on Android and Windows. Free download.
home: /en/
other_lang: /
other_lang_code: de
nav:
  - title: Download
    url: /en/#download
  - title: Guide
    url: /en/guide/
---

<section class="hero" id="download">
  <img src="{{ '/assets/icon.png' | relative_url }}" alt="My Audio">
  <h1>My Audio</h1>
  <p class="tagline">Your idea. Your demo.</p>
  <p class="lead">Record track by track, mix with effects and share – on your phone and on your PC. No account, no cloud, free and open source.</p>
  <div class="buttons">
    <a class="btn" href="{{ site.repository_url }}/releases/download/v{{ site.release }}/MyAudio-{{ site.release }}.apk">
      <span class="title">Android</span><span class="sub">APK · Android 10+ · signed</span>
    </a>
    <a class="btn" href="{{ site.repository_url }}/releases/download/v{{ site.release }}/MyAudio-{{ site.release }}.msi">
      <span class="title">Windows</span><span class="sub">MSI · Windows 10/11</span>
    </a>
  </div>
  <p class="meta">Version {{ site.release }} of {{ site.released | date: "%Y-%m-%d" }} ·
    <a href="{{ site.repository_url }}/releases/tag/v{{ site.release }}">checksums and release notes</a> ·
    <a href="{{ site.repository_url }}/releases">all versions</a></p>
</section>

<div class="cards">
  <div class="card"><h3>Record</h3><p>New tracks in time with the existing ones, calibrated to your device.</p></div>
  <div class="card"><h3>Live effects</h3><p>Monitor through reverb, EQ or amp effects while recording – the take stays dry.</p></div>
  <div class="card"><h3>Mix</h3><p>Volume, panning, envelopes and up to six effects per track and on the master.</p></div>
  <div class="card"><h3>Practice</h3><p>Metronome, practice loop, guitar tuner and lyrics with chords.</p></div>
  <div class="card"><h3>Share</h3><p>Mix as Ogg/Opus, stems as WAV, whole projects as ZIP.</p></div>
  <div class="card"><h3>Open</h3><p>MIT licensed, with 478 Airwindows effects. Everything stays local on your device.</p></div>
</div>

<img class="shot" src="{{ '/images/project.png' | relative_url }}" alt="Project view with four tracks, loop and master">

## Installation

**Android:** download the APK and open it. Android asks once whether your browser or file manager may install apps. Install updates the same way over the existing app; your projects are kept.

**Windows:** download the MSI and run it. The installer is not signed with a code-signing certificate, so Windows SmartScreen may ask (“More info” → “Run anyway”). My Audio is installed for your user only; projects live in *Documents/My Audio* and are kept when you uninstall.

If you like, compare the file's SHA-256 checksum with the one in the [release]({{ site.repository_url }}/releases/tag/v{{ site.release }}).

## How it works

The [guide]({{ '/en/guide/' | relative_url }}) explains every screen and function, from the first take to the shared mix. The screenshots show the German interface. Source code, licenses and version history are on [GitHub]({{ site.repository_url }}).
