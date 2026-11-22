---
layout: default
lang: de
title: Mehrspur-Recorder für Musikdemos
description: My Audio – Spur für Spur aufnehmen, mischen und teilen auf Android und Windows. Kostenloser Download.
home: /
other_lang: /en/
other_lang_code: en
nav:
  - title: Download
    url: /#download
  - title: Anleitung
    url: /anleitung/
---

<section class="hero" id="download">
  <img src="{{ '/assets/icon.png' | relative_url }}" alt="My Audio">
  <h1>My Audio</h1>
  <p class="tagline">Deine Idee. Dein Demo.</p>
  <p class="lead">Spur für Spur aufnehmen, mit Effekten mischen und teilen – auf dem Telefon und am PC. Ohne Konto, ohne Cloud, kostenlos und quelloffen.</p>
  <div class="buttons">
    <a class="btn" href="{{ site.repository_url }}/releases/download/v{{ site.release }}/MyAudio-{{ site.release }}.apk">
      <span class="title">Android</span><span class="sub">APK · ab Android 10 · signiert</span>
    </a>
    <a class="btn" href="{{ site.repository_url }}/releases/download/v{{ site.release }}/MyAudio-{{ site.release }}.msi">
      <span class="title">Windows</span><span class="sub">MSI · Windows 10/11</span>
    </a>
  </div>
  <p class="meta">Version {{ site.release }} vom {{ site.released | date: "%d.%m.%Y" }} ·
    <a href="{{ site.repository_url }}/releases/tag/v{{ site.release }}">Prüfsummen und Versionshinweise</a> ·
    <a href="{{ site.repository_url }}/releases">alle Versionen</a></p>
</section>

<div class="cards">
  <div class="card"><h3>Aufnehmen</h3><p>Neue Spuren im Takt zu den bestehenden, eingemessen auf dein Gerät.</p></div>
  <div class="card"><h3>Live-Effekte</h3><p>Beim Aufnehmen durch Hall, EQ oder Verstärker mithören – die Aufnahme bleibt trocken.</p></div>
  <div class="card"><h3>Mischen</h3><p>Lautstärke, Panorama, Verlaufskurven und bis zu sechs Effekte je Spur und im Master.</p></div>
  <div class="card"><h3>Üben</h3><p>Metronom, Übungsschleife, Gitarrenstimmgerät und Songtext mit Akkorden.</p></div>
  <div class="card"><h3>Teilen</h3><p>Mix als Ogg/Opus, Einzelspuren als WAV, ganze Projekte als ZIP.</p></div>
  <div class="card"><h3>Offen</h3><p>MIT-lizenziert, mit 478 Airwindows-Effekten. Alles bleibt lokal auf deinem Gerät.</p></div>
</div>

<img class="shot" src="{{ '/images/project.png' | relative_url }}" alt="Projektansicht mit vier Spuren, Schleife und Master">

## Installation

**Android:** Lade die APK herunter und öffne sie. Android fragt einmalig, ob dein Browser oder Dateimanager Apps installieren darf. Updates installierst du genauso über die vorhandene App; deine Projekte bleiben erhalten.

**Windows:** Lade die MSI herunter und starte sie. Da der Installer nicht mit einem Code-Signing-Zertifikat signiert ist, kann Windows SmartScreen nachfragen („Weitere Informationen“ → „Trotzdem ausführen“). My Audio wird nur für deinen Benutzer installiert; Projekte liegen unter *Dokumente/My Audio* und bleiben bei einer Deinstallation erhalten.

Prüfe bei Bedarf die SHA-256-Prüfsumme der Datei mit den Angaben im [Release]({{ site.repository_url }}/releases/tag/v{{ site.release }}).

## Wie es funktioniert

Die [Anleitung]({{ '/anleitung/' | relative_url }}) erklärt jeden Bildschirm und jede Funktion, vom ersten Take bis zum geteilten Mix. Den Quelltext, die Lizenzen und die Versionsgeschichte findest du auf [GitHub]({{ site.repository_url }}).
