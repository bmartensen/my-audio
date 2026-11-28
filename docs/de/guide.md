---
layout: default
lang: de
title: Anleitung
description: Die Anleitung zu My Audio – jeder Bildschirm und jede Funktion erklärt.
permalink: /de/guide/
---

# Anleitung

My Audio ist ein Studio auf dem Smartphone. Du hältst eine Idee fest, nimmst Spur für Spur dazu auf, gibst Effekte dazu und teilst das Ergebnis. Diese Anleitung erklärt jeden Bildschirm. Die Bilder stammen von Android. Unter Windows sieht alles gleich aus und wird mit der Maus bedient.

Stand: My Audio {{ site.release }}. Die verfügbaren Installationsdateien stehen auf der [Download-Seite]({{ '/de/#download' | relative_url }}).

<nav class="toc" markdown="1">
- [Erste Schritte](#erste-schritte)
- [Startseite](#startseite)
- [Projektansicht](#projektansicht)
- [Spuren](#spuren)
- [Die Aufnahmespur](#die-aufnahmespur)
- [Aufnehmen und Abspielen](#aufnehmen-und-abspielen)
- [Spur-Einstellungen](#spur-einstellungen)
- [Effekte am Beispiel Hall](#effekte-am-beispiel-hall)
- [EQ und Airwindows](#eq-und-airwindows)
- [Live mithören](#live-mithören)
- [Master](#master)
- [Lautstärkeverlauf](#lautstärkeverlauf)
- [Notizen und Akkorde](#notizen-und-akkorde)
- [Metronom](#metronom)
- [Projekteinstellungen](#projekteinstellungen)
- [Stimmgerät](#stimmgerät)
- [Teilen und Sichern](#teilen-und-sichern)
- [SoundCraft](#soundcraft)
- [Effekt-Bibliothek](#effekt-bibliothek)
- [Amp-Modelle und Boxen](#amp-modelle-und-boxen)
- [Einstellungen](#einstellungen)
- [Latenz einmessen](#latenz-einmessen)
- [Windows](#windows)
- [Daten und Datenschutz](#daten-und-datenschutz)
</nav>

## Erste Schritte

1. **Installieren:** siehe [Download]({{ '/de/#download' | relative_url }}).
2. **Einmessen:** Öffne **Einstellungen → Latenz einmessen** und tippe auf **Einmessung starten**. So liegen neue Aufnahmen genau im Takt zu den bestehenden Spuren. Aufnehmen kannst du auch ohne Einmessung. Die Spuren können dann leicht verzögert sein.
3. **Aufnehmen:** Tippe auf der Startseite auf den roten Aufnahmeknopf. My Audio legt ein neues Projekt an und beginnt sofort mit der ersten Spur.

Kopfhörer sind beim Überspielen und Mithören immer die beste Wahl.

## Startseite

<img class="shot" src="{{ '/images/home.png' | relative_url }}" alt="Startseite mit Projekten, Neues Projekt und Aufnahmeknopf">

- **Projektkarte:** Tippen öffnet das Projekt. Darunter stehen die Zahl der Spuren und die Länge. Der Stift benennt das Projekt um, der Papierkorb löscht es mit allen Aufnahmen.
- **Aufnahmeknopf (rot):** legt ein Projekt mit Datum und Uhrzeit als Namen an und startet die erste Aufnahme.
- **Neues Projekt** unter der Liste legt ein leeres Projekt an, ohne aufzunehmen.
- **Effekt-Bibliothek** (Wellensymbol): alle Effekte zum Ausprobieren und Auswählen, siehe [Effekt-Bibliothek](#effekt-bibliothek).
- **Projekt importieren** (Pfeil nach unten): öffnet eine ZIP-Datei. Eine Sicherung aus **Teilen → Projekt sichern** wird als Projekt wiederhergestellt. Eine gezippte SoundCraft-Sitzung wird zu einem Projekt mit allen Spuren an ihren Positionen. Ein ZIP mit Audiodateien (MP3, Ogg, WAV) wird zu einem neuen Projekt mit einer Spur je Datei. Eine führende Nummer im Dateinamen („01 Drums.wav“) bestimmt die Reihenfolge.
- **Einstellungen** (Zahnrad): Geräte, Audiopuffer, Einmessung und Infos.

## Projektansicht

<img class="shot" src="{{ '/images/project.png' | relative_url }}" alt="Projektansicht">

Oben stehen **Zurück**, der Projektname, **Rückgängig**, **Notizen**, **Teilen** und **Projekteinstellungen**. **Rückgängig** nimmt jede Änderung im Projekt zurück, auch eine Aufnahme oder eine gelöschte Spur, solange das Projekt offen ist. Danach erscheint links daneben **Wiederholen**. Darunter folgen:

- **Hinweis zur Einmessung**, falls die gewählten Geräte noch nicht eingemessen sind.
- **Übungsschleife:** Ziehe auf der Leiste einen Bereich auf. Ein kurzes Tippen setzt die [Startmarke](#aufnehmen-und-abspielen). Der Knopf links schaltet die Schleife ein und aus, der Text zeigt ihren Bereich. Ist sie aktiv, beginnt **Abspielen** am Schleifenanfang und wiederholt den Bereich nahtlos.
- **Master:** die Effekte auf dem ganzen Mix. Tippen öffnet sie (siehe [Master](#master)).
- **Spuren** und ganz unten die **Aufnahmespur**.
- **Transportleiste:** Zeit, Abspielen, Aufnehmen und Metronom.

<img class="shot landscape" src="{{ '/images/project-landscape.png' | relative_url }}" alt="Projekt im Querformat mit mittig angeordnetem Aufnahmeknopf">

## Spuren

<img class="shot" src="{{ '/images/project-record-track.png' | relative_url }}" alt="Spuren mit Effektzeile und Lautstärkeverlauf">

Jede Karte zeigt eine Aufnahme auf der gemeinsamen Zeitachse:

- **Name und Länge**, dahinter die Lautstärke, wenn sie von 0 dB abweicht.
- **Lautstärkeverlauf** (Kurvensymbol, farbig, wenn ein Verlauf gesetzt ist): öffnet den [Editor](#lautstärkeverlauf).
- **S – Solo:** Sobald eine Spur auf Solo steht, klingen nur noch die Solo-Spuren.
- **Lautsprecher – Stumm:** schaltet die Spur stumm. Solo hat Vorrang.
- **Papierkorb:** löscht die Spur.
- **Effektzeile:** die eingeschalteten Effekte in Reihenfolge.
- **Ohne Einmessung aufgenommen:** Diese Spur entstand ohne Latenzausgleich und kann leicht hinter den anderen liegen. Unter **Timing** in den [Spur-Einstellungen](#spur-einstellungen) trägst du ihre Latenz nach.

Die Wellenform steht auf der gemeinsamen Zeitachse des Projekts. Ihre leicht hinterlegte Fläche reicht vom Einsatz der Spur bis zu ihrem Ende, so siehst du auch einen verschobenen Beginn.

Ein langer Druck auf die Karte öffnet die [Spur-Einstellungen](#spur-einstellungen). Während der Wiedergabe springst du durch Tippen auf eine Wellenform an diese Stelle. Im Stillstand setzt Tippen die Startmarke.

## Die Aufnahmespur

Mit **Neue Spur** unter den Spuren bereitest du eine leere Aufnahmespur für den nächsten Take vor, auch in einem Projekt ohne Aufnahmen. Sie zeigt **Leer**, und du kannst das Instrument schon vor der Aufnahme einstellen. Der nächste Take landet in dieser Spur.

- **Effekte** (Wellensymbol) oder langer Druck öffnet ihre Einstellungen: Name, Effekte, Lautstärke und Panorama.
- **Stumm** bedeutet bei der leeren Aufnahmespur: Das Mikrofon wird beim Aufnehmen nicht mitgehört. Hebst du die Stummschaltung auf, steht **Mithören** auf der Karte, und du hörst dich beim Aufnehmen durch ihre Effekte (siehe [Live mithören](#live-mithören)).

Du kannst auch ohne vorbereitete Spur direkt auf **Aufnehmen** tippen. Dann übernimmt die neue Spur Effekte und Eingangseinstellungen der letzten Aufnahme.

## Aufnehmen und Abspielen

- **Startmarke:** Tippe im Stillstand auf die Schleifenleiste oder eine Wellenform. Abspielen und Aufnehmen beginnen dort, Stopp springt wieder zurück. Die Zeit zeigt die Marke an.
- **Abspielen** startet an der Startmarke oder am Beginn der aktiven Schleife. Nochmal tippen stoppt.
- **Aufnehmen** startet einen Take an der Startmarke. Alle hörbaren Spuren laufen mit, die neue Spur wird gleichzeitig aufgenommen. Ist ein **Vorlauf** eingestellt, zählt erst ein stiller Countdown herunter. Ist das Metronom an, zählt es einen Takt vor.
- **Metronom** öffnet die [Metronom-Einstellungen](#metronom).
- Mit einem USB-Interface mit zwei Eingängen erscheint unter der Zeit die Wahl **Eingang 1**, **Eingang 2** oder **Eingang 1 und 2 gemischt**. Aufnahmen sind immer mono.
- Manche Interfaces geben trotz mehrerer Eingänge nur den ersten an My Audio weiter, dann wird nur dieser aufgenommen. Bleibt eine Aufnahme über einen anderen Eingang still, prüfe, ob dein Interface eine „Combine“-Funktion hat, die die Eingänge auf den ersten legt – oft in der Software des Herstellers am Computer.

Aufnahmen werden immer ohne Effekte gespeichert. Effekte, Lautstärke und Verlauf wirken nur beim Abspielen und Teilen – du kannst sie jederzeit ändern. Auf Android laufen Wiedergabe und Aufnahme mit Benachrichtigung auch im Hintergrund und bei ausgeschaltetem Bildschirm weiter.

## Spur-Einstellungen

<img class="shot" src="{{ '/images/track-settings.png' | relative_url }}" alt="Spur-Einstellungen">

Ein langer Druck auf eine Spur öffnet ihre Einstellungen in Signalreihenfolge. Der **Stift** benennt die Spur um. **Fertig** übernimmt, **Abbrechen** verwirft alle Änderungen.

**Eingang**
- **Metronom entfernen** – nur bei Takes, die mit hörbarem Klick aufgenommen wurden: rechnet den mitaufgenommenen Klick heraus.
- **Übersprechen entfernen** – nur bei Takes, die über Lautsprecher-Wiedergabe aufgenommen wurden: entfernt die anderen Spuren, die der Lautsprecher in die Aufnahme gespielt hat.
- **Normalisieren** – bringt die Spitze der Aufnahme auf einen Zielpegel.
- **Timing** – **Versatz** verschiebt die Spur in Millisekunden, mit **−** und **+** oder per Eingabe. **Latenz** zeigt den eingemessenen Ausgleich. Bei einer Spur ohne Einmessung trägst du ihn selbst ein oder übernimmst mit **Einmessung übernehmen** die aktuelle Messung.

**Effekte** – bis zu sechs, von oben nach unten durchlaufen:
- **Effekt hinzufügen** bietet die in der [Bibliothek](#effekt-bibliothek) ausgewählten Effekte an.
- **Griff links** (zwei Striche): ziehen ändert die Reihenfolge.
- **Häkchen:** schaltet den Effekt aus, ohne seine Einstellungen zu verlieren.
- **Papierkorb:** entfernt den Effekt.
- Tippen auf den **Namen** klappt den Effekt mit seinen Reglern auf.

**Kanal**
- **Lautstärke** von stumm bis +6 dB.
- **Panorama** (bei Stereo-Importen **Balance**) von L 100 bis R 100.

## Effekte am Beispiel Hall

<img class="shot" src="{{ '/images/effect-reverb.png' | relative_url }}" alt="Hall mit Reglern">

**Hall (My Audio)** setzt die Spur in einen Raum, von der kleinen Kammer bis zur Kathedrale.

| Regler | Wirkung |
|---|---|
| **Raum** | Vorlage **Kammer**, **Raum**, **Saal**, **Kirche** oder **Kathedrale**. |
| **Nachhallzeit** | 0,2 bis 10 s – wie lange der Hall braucht, um zu verklingen (RT60). Größere Werte klingen nach größerem Raum. |
| **Pre-Delay** | 0 bis 200 ms Pause vor dem ersten Echo. Etwas Pre-Delay hält Gesang und Instrumente vorne und deutlich. |
| **Höhendämpfung** | 0 % wie Stein (Höhen klingen lange nach) bis 100 % wie ein weicher Raum (Höhen verklingen schnell). |
| **Anteil** | 0 % trocken, 50 % gleich viel Raum wie Direktklang, 100 % nur Hall. |

Der Hall arbeitet in Stereo. Eine Mono-Spur wird dadurch räumlich.

### Probehören und vergleichen

<img class="shot wide" src="{{ '/images/effect-preview.png' | relative_url }}" alt="Probehören mit Mit/Ohne-Schalter">

- **Probehören** spielt einen etwa sechs Sekunden langen Ausschnitt in Schleife.
- **Mit / Ohne** vergleicht die Kette mit und ohne diesen Effekt.
- **Live** schickt stattdessen dein Mikrofon durch die Effekte, siehe [Live mithören](#live-mithören).

## EQ und Airwindows

**EQ (My Audio)** kombiniert Equalizer und Kompressor:
- **Klang:** Vorlagen **Gesang**, **Gitarre**, **Klavier** und **Neutral**.
- **Equalizer:** **Tiefen** (150 Hz), **Präsenz** (3 kHz) und **Höhen** (8 kHz), je ±12 dB, mit eigenem Schalter.
- **Kompressor:** ein Regler **Stärke** (0–100 %) stellt Schwelle und Verhältnis zusammen ein. Die Lautstärke wird automatisch ausgeglichen.

<img class="shot" src="{{ '/images/effect-airwindows.png' | relative_url }}" alt="Airwindows-Effekt ToTape9 mit Reglern">

**Airwindows** sind 478 Effekte von Chris Johnson – von Bandmaschine über Verzerrer bis Hall. Jeder hat eigene Regler mit eigenen Einheiten, eine kurze Beschreibung und:
- **Standardwerte** setzt alle Regler zurück.
- **Pegel ausgleichen** hält die Lautheit gleich, wenn der Effekt eingeschaltet wird.

## Live mithören

My Audio kann dein Mikrofon in Echtzeit durch Effekte schicken, während du spielst oder singst:

1. **Beim Aufnehmen:** Hebe die Stummschaltung der leeren Aufnahmespur auf. Während der Aufnahme hörst du dich durch ihre Effekte, zusammen mit den anderen Spuren.
2. **Beim Ausprobieren:** In der Bibliothek und in den Spur-Einstellungen startet **Live** das Mikrofon direkt durch den Effekt zum Ausgang – ohne Wiedergabe und ohne Aufnahme.

<img class="shot dialog" src="{{ '/images/live-warning.png' | relative_url }}" alt="Rückfrage vor dem Mithören über Bluetooth">

- Verwende Kopfhörer. Am eingebauten Lautsprecher droht Rückkopplung (Pfeifen), über Bluetooth kommt der Klang deutlich verzögert an.
- Die Aufnahme bleibt trocken. Gespeichert wird immer das unbearbeitete Mikrofon. Die Effekte wirken danach genau so wie beim Mithören.
- Die Verzögerung beim Mithören entspricht der eingemessenen Latenz. Mit dem [Audiopuffer](#einstellungen) lässt sie sich verkürzen.

## Master

<img class="shot dialog" src="{{ '/images/master.png' | relative_url }}" alt="Master-Effekte">

Der Master bearbeitet den ganzen Mix – mit derselben Effektkette wie eine Spur, aber ohne Eingang und Kanal. Danach folgt immer eine feste Endstufe: Lautheit auf −14 LUFS und ein Begrenzer bei −1 dBFS. So klingen geteilte Mixe gleich laut.

## Lautstärkeverlauf

<img class="shot" src="{{ '/images/envelope.png' | relative_url }}" alt="Editor für den Lautstärkeverlauf">

Mit dem Kurvensymbol einer Spur zeichnest du ein Ein- oder Ausblenden oder einzelne leisere Stellen:

- Tippen in die **Wellenform** setzt einen Punkt, Ziehen verschiebt ihn. Die Höhe reicht von 0 % (unten) bis 100 % (oben) der Spurlautstärke.
- Der **Papierkorb** entfernt den gewählten Punkt. **Auf volle Lautstärke zurücksetzen** entfernt alle Punkte.
- Die **Übersicht** unter der Wellenform zeigt die ganze Spur: Tippen zentriert, Ziehen verschiebt. Die **Lupen** zoomen von 1- bis 8-fach.
- **Spur solo vorhören** (▶) spielt die Spur mit dem neuen Verlauf und ihren Effekten. Währenddessen springst du durch Tippen in die Wellenform.
- **Speichern** übernimmt den Verlauf.

## Notizen und Akkorde

<img class="shot" src="{{ '/images/notes.png' | relative_url }}" alt="Notizen mit hervorgehobenen Akkorden">

Das Notizsymbol oben zeigt den Songtext des Projekts anstelle der Spuren.

- Akkorde werden automatisch hervorgehoben – deutsche und englische Namen, Erweiterungen und Slash-Akkorde wie *Am/G*.
- **Lupen** ändern die Schriftgröße, der **Stift** öffnet den Editor, das **X**, das Notizsymbol oder **Zurück** zeigen wieder die Spuren.
- Mit **Notizen mitlaufen lassen** in den [Projekteinstellungen](#projekteinstellungen) rollt der Text beim Spielen wie ein Teleprompter mit.

## Metronom

<img class="shot dialog" src="{{ '/images/metronome.png' | relative_url }}" alt="Metronom-Einstellungen">

- **Metronom an:** Der Klick ist beim Aufnehmen zu hören und zählt einen Takt vor.
- **Auch bei Wiedergabe:** Klick auch beim Abspielen.
- **Tempo:** 40 bis 240 BPM mit − / + oder Regler.
- **Lautstärke** des Klicks.

Einen mitaufgenommenen Klick kann **Metronom entfernen** in den Spur-Einstellungen später herausrechnen.

## Projekteinstellungen

<img class="shot dialog" src="{{ '/images/project-settings.png' | relative_url }}" alt="Projekteinstellungen">

- **Spur importieren:** eine MP3-, Ogg- oder WAV-Datei als neue Spur ab Projektanfang (mono oder stereo).
- **Gitarrenstimmgerät:** siehe [Stimmgerät](#stimmgerät).
- **Notizen mitlaufen lassen:** Abspielen und Aufnehmen starten oben in den Notizen. Nach vier Zeilen rollt der Text weiter, Tempo 0,5 bis 20 s pro Zeile.
- **Vorlauf vor der Aufnahme:** ein stiller Countdown von 1 bis 30 s, um dich ans Instrument zu setzen.

## Stimmgerät

<img class="shot dialog" src="{{ '/images/tuner.png' | relative_url }}" alt="Gitarrenstimmgerät">

Schlage eine leere Saite an. Das Stimmgerät erkennt die Saite automatisch (Standardstimmung E–A–D–G–H–E, A = 440 Hz) oder du tippst eine Saite an. Die Skala zeigt die Abweichung in Cent. Innerhalb von ±5 Cent erscheint **Stimmt**.

## Teilen und Sichern

<img class="shot wide" src="{{ '/images/share.png' | relative_url }}" alt="Teilen-Menü">

- **Mix teilen:** genau das, was **Abspielen** hören lässt, als Stereo-Ogg/Opus – klein genug für Messenger.
- **Mix als MP3:** derselbe Mix als MP3, für jeden Player und jedes Upload-Formular.
- **Einzelspuren:** jede Spur als bereinigte WAV-Datei ab Projektanfang, ohne Effekte, Lautstärke und Panorama, in einem ZIP – zum Weiterarbeiten in einer DAW.
- **Projekt sichern:** das ganze Projekt mit allen Originalaufnahmen und Einstellungen als ZIP. **Projekt importieren** auf der Startseite stellt es wieder her, auch auf einem anderen Gerät.
- **SoundCraft-Sitzung:** das Projekt als Sitzung für die Desktop-DAW SoundCraft, als ZIP (siehe [SoundCraft](#soundcraft)).

## SoundCraft

[SoundCraft](https://github.com/storytold/soundcraft) ist eine quelloffene DAW für den PC. Mit ihr verschiebst und schneidest du Spuren, die du unterwegs aufgenommen hast, und nimmst die Sitzung danach wieder mit aufs Telefon.

- **Teilen → SoundCraft-Sitzung** erzeugt ein ZIP. Entpacke es am PC und öffne die *.scraft*-Datei in SoundCraft.
- Jede Spur liegt dort an ihrer Stelle im Song, mit Lautstärke, Panorama, Stummschaltung und Lautstärkeverlauf.
- **EQ (My Audio)** mit **Kompressor** und **Hall (My Audio)** werden zu den passenden SoundCraft-Effekten und bleiben einstellbar. Sie klingen dort ähnlich, aber nicht exakt gleich.
- Airwindows-Effekte, Amp-Modelle und Boxen gibt es in SoundCraft nicht. Sie sind in die Spur eingerechnet, die unbearbeitete Aufnahme liegt in der Playlist „Dry“ daneben.
- Zurück aufs Telefon: Speichere die Sitzung in SoundCraft, packe ihren Ordner als ZIP und öffne es mit **Projekt importieren**. Jede Spur behält ihre Position, auch wenn du dort Clips verschoben hast. Danach nimmst du weiter auf, fügst Effekte hinzu und exportierst die Sitzung erneut.
- Was My Audio nicht übernehmen kann, etwa fremde Plugins oder MIDI-Spuren, steht danach in den **Notizen** des Projekts.

Wechselst du auf ein neues Android-Telefon, nimmt die Datenübertragung von Android deine Projekte und die Effekt-Bibliothek mit. Auf dem neuen Telefon misst du neu ein.

## Effekt-Bibliothek

<div class="shots">
  <img class="shot" src="{{ '/images/library.png' | relative_url }}" alt="Effekt-Bibliothek">
  <img class="shot" src="{{ '/images/library-preview.png' | relative_url }}" alt="Probehören in der Bibliothek">
</div>

Die Bibliothek enthält **EQ (My Audio)**, **Hall (My Audio)**, deine importierten Amp-Modelle und Boxen und 478 Airwindows-Effekte.

- **Suchen** nach Name oder Beschreibung, **Filter** nach **Alle**, **Ausgewählt** oder Kategorie.
- Tippen auf einen **Effekt** klappt ihn mit Beschreibung und Reglern auf. **▶** spielt ihn sofort an. Aufgeklappt gibt es **Probehören** und **Live** mit **Mit / Ohne**.
- Das **Häkchen** wählt einen Effekt für **Effekt hinzufügen** in den Spuren aus.

## Amp-Modelle und Boxen

Ein Amp-Modell ist ein Profil eines echten Verstärkers oder Pedals, gelernt mit dem [Neural Amp Modeler](https://www.neuralampmodeler.com). Es klingt wie das Gerät selbst. Eine Box-Impulsantwort (IR) bildet den Lautsprecher und das Mikrofon davor nach. Zusammen ergeben sie einen kompletten Gitarren- oder Bass-Sound. Viele Profile enthalten die Box bereits, dann brauchst du keine IR.

Unter **Amp-Modelle und Boxen** in der Bibliothek holst du dir solche Profile (*.nam*) und Impulsantworten (*.wav*) in die App, zum Beispiel von [TONE3000](https://www.tone3000.com). Lade sie im Browser herunter und tippe auf **Importieren**. Eine heruntergeladene ZIP-Datei kannst du direkt auswählen, die App übernimmt alle Profile und Impulsantworten daraus.

- Importierte Tones sind gleich ausgewählt und stehen unter **Effekt hinzufügen** bereit, live und beim Abspielen.
- Ein Amp-Modell hat **Eingang** und **Ausgang**, beide Arten haben **Pegel ausgleichen**.
- **Tone entfernen** löscht ihn von diesem Gerät. Projekt-Sicherungen enthalten die verwendeten Tones.
- Beachte die Lizenz, die auf der Seite des jeweiligen Tones steht, wenn du Aufnahmen damit veröffentlichst.

## Einstellungen

<img class="shot" src="{{ '/images/settings.png' | relative_url }}" alt="Einstellungen">

- **Wiedergabegerät** und **Mikrofon:** alle angeschlossenen Geräte. Ein Bluetooth-Headset-Mikrofon nimmt in Telefonqualität auf und spielt dann über den Telefonlautsprecher.
- **Audiopuffer:** wie viel Audio Ein- und Ausgang zwischenspeichern.
  - **Automatisch** nutzt den Standard des Systems für geringe Latenz. Für die meisten Geräte die beste Wahl.
  - **Kurz** verringert die Verzögerung beim Live-Mithören, kann aber zu Knacksern und Aussetzern führen.
  - **Lang** ist am robustesten, wenn es knackst – mit etwas mehr Verzögerung.
- **Latenz einmessen:** zeigt die gemessene Latenz und öffnet die Einmessung.

## Latenz einmessen

<img class="shot" src="{{ '/images/calibration.png' | relative_url }}" alt="Einmessung mit Ergebnis">

Jedes Gerät braucht etwas Zeit, bis ein Ton aus dem Lautsprecher kommt und wieder im Mikrofon ankommt. Die Einmessung spielt kurze Sweeps ab, nimmt sie auf und misst diese Laufzeit. Damit legt My Audio jede neue Aufnahme genau auf die bestehenden Spuren.

- Leg das Telefon frei hin, sorg für Ruhe und stell die Medienlautstärke auf etwa zwei Drittel. Tippe auf **Einmessung starten** bzw. **Erneut einmessen**.
- Das Ergebnis zeigt **Latenz**, **Stabilität** und **Signal**.
- Gemessen wird je Kombination aus Wiedergabegerät, Mikrofon und Audiopuffer. Bluetooth muss nach einem neuen Verbinden neu gemessen werden.

## Windows

Die Windows-Version hat dieselben Funktionen und denselben Aufbau, bedient mit Maus und Tastatur:
- Ein Rechtsklick auf eine Spur oder den **Master** öffnet ihre Einstellungen, wie langes Drücken.
- Geräte, Audiopuffer und Einmessung liegen im Einstellungsdialog (Zahnrad).
- Projekte liegen unter *Dokumente/My Audio*. ZIP- und Audiodateien lassen sich auch per Drag-and-drop importieren, mehrere auf einmal. Jedes ZIP wird ein Projekt, zusammen abgelegte Audiodateien werden ein Projekt mit einer Spur je Datei. Speichern und Teilen öffnen einen Speichern-Dialog.
- **Strg+Z** nimmt eine Änderung zurück, **Strg+Y** stellt sie wieder her.
- **F1** öffnet **Über My Audio**.
- Windows unterscheidet Lautsprecher und Kopfhörer nicht. Achte beim Live-Mithören selbst auf Kopfhörer, damit keine Rückkopplung entsteht.

## Daten und Datenschutz

My Audio braucht kein Konto und keinen Internetdienst. Projekte bleiben auf deinem Gerät (Android: privater App-Speicher, Windows: *Dokumente/My Audio*). Was du teilst, entscheidest du selbst über **Teilen**. Sicherungen enthalten alle Originalaufnahmen. Einzelheiten stehen in der [Datenschutzerklärung]({{ '/de/privacy/' | relative_url }}).

---

Fragen, Ideen oder Fehler? Der Quelltext und die Versionsgeschichte liegen auf [GitHub]({{ site.repository_url }}).
