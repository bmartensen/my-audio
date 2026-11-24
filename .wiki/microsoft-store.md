# Microsoft Store

Everything Partner Center asks for when submitting the Windows app, in the order of its pages. The listing texts follow the guide style: what the app does, no filler, no semicolons. German and English listings are deliberate localizations. Package and signing: [issue #18](https://github.com/bmartensen/my-audio/issues/18) and [decisions](decisions.md).

## Product identity

Reserved name **My Audio**. The package identity lives in `gradle.properties` and is not secret:

| Field | Value |
|---|---|
| Package/Identity/Name | `58153Martensening.com.MyAudio` |
| Package/Identity/Publisher | `CN=95F712C4-BF0B-424B-BFD9-2B54E79107E3` |
| Package/Properties/PublisherDisplayName | `Martensening.com` |

## Pricing and availability

| Field | Value |
|---|---|
| Markets | All markets |
| Visibility | Public audience, discoverable in the Store |
| Pricing | Free |
| Free trial | No free trial |
| Release date | As soon as it passes certification |

## Properties

| Field | Value |
|---|---|
| Category | Music |
| Privacy policy URL | `https://bmartensen.github.io/my-audio/en/privacy/` |
| Website | `https://bmartensen.github.io/my-audio/` |
| Support contact info | `info@martensening.com` |
| Product declarations | Install to alternate drives allowed. Everything else not ticked. |
| Minimum hardware | Microphone |
| Recommended hardware | Nothing ticked |

## Age ratings

IARC questionnaire, app category *All other app types*. Every question about violence, sexuality, language, controlled substances and gambling is answered **No**, as are the questions about user interaction, sharing location, digital purchases and unrestricted web access. Expected result: suitable for all ages.

## Packages

The MSIX built for [#18](https://github.com/bmartensen/my-audio/issues/18), architecture x64. Device family Windows 10/11 Desktop.

## Store listing de-DE

**Product name:** My Audio

**Description:**

> My Audio ist ein Studio für spontane Musikideen. Nimm Spur für Spur auf, während du die bisherigen Spuren hörst. Forme den Klang mit Effekten und teile dein Demo.
>
> Aufnehmen. Ein Klick startet ein neues Projekt mit der ersten Spur. Die Einmessung gleicht die Verzögerung deiner Audiogeräte aus, damit neue Takes im Takt liegen. Metronom mit Einzähler und stiller Vorlauf helfen beim Einstieg. Mit der Startmarke nimmst du an jeder Stelle im Song auf. Jede Änderung lässt sich rückgängig machen.
>
> Klang. Lautstärke, Panorama, Solo und Stumm für jede Spur. Bis zu sechs Effekte pro Spur und auf dem Master, darunter EQ, Kompressor, Hall und 478 Airwindows-Effekte. Eigene Amp-Modelle und Boxen lassen sich importieren. Jeder Effekt lässt sich vor dem Einsetzen probehören, auch live über das Mikrofon. Ein Lautstärkeverlauf hebt Passagen an oder blendet sie aus.
>
> Ideen festhalten. Songtext und Akkorde stehen in den Notizen jedes Projekts, auf Wunsch als mitlaufender Teleprompter. Ein Stimmgerät für Gitarre ist eingebaut.
>
> Teilen. Exportiere den Mix als Ogg/Opus, die Einzelspuren als WAV, das ganze Projekt als Sicherung oder als Sitzung für SoundCraft.
>
> Alles bleibt auf deinem Rechner. Du brauchst kein Konto und keine Internetverbindung. My Audio gibt es auch für Android, und Projekte lassen sich zwischen beiden austauschen. My Audio ist Open Source unter der MIT-Lizenz.

**What's new in this version:** leave empty for the first submission. Later the changelog entry of the version.

**Product features:**

- Mehrspuraufnahme mit eingemessener Latenz
- Aufnehmen ab jeder Stelle im Song
- Rückgängig und Wiederholen für jede Änderung
- Bis zu sechs Effekte pro Spur und auf dem Master
- EQ, Kompressor, Hall und 478 Airwindows-Effekte
- Import eigener Amp-Modelle und Boxen
- Live mithören durch die Effekte
- Lautstärkeverlauf und Zeitversatz pro Spur
- Notizen mit Akkorden und Teleprompter
- Metronom mit Einzähler und Stimmgerät
- Export als Ogg/Opus, WAV-Einzelspuren, Projektsicherung und SoundCraft-Sitzung
- Ohne Konto und ohne Internet

**Screenshots** (`branding/microsoft-store/screenshots/de-DE/`, in this order, with captions):

1. Deine Projekte auf einen Blick
2. Spuren aufnehmen und mischen
3. Effekte für jede Spur
4. Lautstärke im Verlauf formen
5. Alle Effekte zum Ausprobieren

**Store logos:** 1:1 app tile icon `branding/microsoft-store/listing/app-tile-icon-300.png`. Box art and poster art are only needed for Xbox and stay empty.

**Super hero art (16:9):** `branding/microsoft-store/listing/super-hero-art-1920x1080.png`.

**Short description:** Nimm Musikideen Spur für Spur auf, forme sie mit Effekten und teile dein Demo. Alles bleibt auf deinem Rechner.

**Additional system requirements:** Minimum hardware *Mikrofon oder Audio-Interface*. Recommended hardware *Kopfhörer*.

**Search terms:** Mehrspur, Recorder, Musikaufnahme, Overdub, Songwriting, Demo aufnehmen, Heimstudio

**Copyright and trademark info:** © 2026 Björn Martensen

**Additional license terms:** Open Source unter der MIT-Lizenz. Quellcode und Lizenzen auf https://github.com/bmartensen/my-audio

**Developed by:** Björn Martensen

## Store listing en-US

**Product name:** My Audio

**Description:**

> My Audio is a studio for spontaneous musical ideas. Record track by track while you hear the tracks you already have. Shape the sound with effects and share your demo.
>
> Record. One click starts a new project with its first track. Calibration compensates the delay of your audio devices, so new takes land in time. A metronome with count-in and a silent pre-roll help you come in. The start marker lets you record from any point in the song. Every change can be undone.
>
> Sound. Volume, pan, solo and mute for every track. Up to six effects per track and on the master, including EQ, compressor, reverb and 478 Airwindows effects. Import your own amp models and cabinets. Audition every effect before you add it, also live through the microphone. A volume envelope brings passages forward or fades them out.
>
> Keep your ideas. Lyrics and chords live in the notes of each project, as a scrolling teleprompter if you like. A guitar tuner is built in.
>
> Share. Export the mix as Ogg/Opus, the individual tracks as WAV, the whole project as a backup or as a session for SoundCraft.
>
> Everything stays on your computer. No account and no internet connection needed. My Audio is also available for Android, and projects move between both. My Audio is open source under the MIT license.

**What's new in this version:** leave empty for the first submission. Later the changelog entry of the version.

**Product features:**

- Multitrack recording with calibrated latency
- Record from any point in the song
- Undo and redo for every change
- Up to six effects per track and on the master
- EQ, compressor, reverb and 478 Airwindows effects
- Import your own amp models and cabinets
- Live monitoring through the effects
- Volume envelope and time offset per track
- Notes with chords and a teleprompter
- Metronome with count-in and a tuner
- Export as Ogg/Opus, WAV stems, project backup and SoundCraft session
- No account and no internet

**Screenshots** (`branding/microsoft-store/screenshots/en-US/`, in this order, with captions):

1. Your projects at a glance
2. Record and mix your tracks
3. Effects for every track
4. Shape the volume over time
5. Every effect to try out

**Store logos and super hero art:** the same files as de-DE.

**Short description:** Record musical ideas track by track, shape them with effects and share your demo. Everything stays on your computer.

**Additional system requirements:** Minimum hardware *Microphone or audio interface*. Recommended hardware *Headphones*.

**Search terms:** multitrack, recorder, music recording, overdub, songwriting, demo recording, home studio

**Copyright and trademark info:** © 2026 Björn Martensen

**Additional license terms:** Open source under the MIT license. Source code and licenses at https://github.com/bmartensen/my-audio

**Developed by:** Björn Martensen

## Submission options

**Notes for certification:**

> My Audio records music from the microphone. No account or sign-in is needed. To test, click the red record button on the home screen, play or sing, then click it again to stop. The take appears as a track and plays with the play button. Calibration under Settings is optional.

**Restricted capability runFullTrust:**

> My Audio is a desktop app built with Compose for Desktop. It runs a bundled Java runtime and native audio through WASAPI, and stores projects in Documents/My Audio. It needs full trust for this, like the MSI version of the same app.

## Images

`tools/prepare_store_assets.py` derives every logo from the master in `assets/source/branding/` without new image generation. Screenshots are captures of the installed app window (about 1086×773, the contributor's own projects, light theme) that `--screenshots <folder>` places on the graphite gradient at 1920×1080, without added logos or text. Captures are named `<language>-<REGION>-<n>-<name>.png`. The English captures come from the same app started with `JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US`. Imported amp models and cabinets stay out of view, because their names carry third-party brands.

| File | Use | Size |
|---|---|---|
| `listing/app-tile-icon-300.png` | 1:1 app tile icon | 300×300 |
| `listing/super-hero-art-1920x1080.png` | 16:9 super hero art, no text or app UI | 1920×1080 |
| `screenshots/de-DE/*.png`, `screenshots/en-US/*.png` | desktop screenshots in listing order | 1920×1080 |
| `package/Square44x44Logo.*` | app icon in the taskbar and start menu (MSIX) | scale 100–400, target sizes 16–256 |
| `package/Square150x150Logo.*`, `package/Wide310x150Logo.*` | start tiles (MSIX) | scale 100 and 200 |
| `package/StoreLogo.*` | package logo (MSIX) | scale 100–400 |

Limits checked on 2026-11-24 in Microsoft's documentation: description 10,000 characters, short description below 270 visible characters, up to 20 features of 200 characters, up to 10 desktop screenshots of at least 1366×768 as PNG with captions of 200 characters, up to 7 search terms.
