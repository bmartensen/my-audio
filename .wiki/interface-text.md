# Interface text

The interface stays clean, elegant and sparse: a label says what a control does, nothing more. Details live in the
[user guide](../docs/), and only when they really matter. These rules apply to every label, menu entry, subtitle,
dialog and hint, in German and English alike.

## Checklist

Draft all siblings of a menu, list or dialog together, in both languages, before building any of them, and check:

1. **One pattern for siblings.** Entries of the same kind share one grammatical form, e.g. verb + object for actions
   ("Share OGG", "Share MP3"; "OGG teilen", "MP3 teilen"). German and English follow the same pattern.
2. **The subtitle adds, never repeats.** It says what the title leaves open, usually the purpose ("for messengers",
   "for any player"), never the title again ("SoundCraft" with "… for SoundCraft").
3. **No technical details.** No file formats or containers (ZIP, Ogg/Opus, WAV in a subtitle when the title already
   implies it), codecs, bit rates, library names, parentheses or annotations. If a detail matters, it goes into the guide.
4. **One line.** A subtitle fits one line at phone width, about 30 characters; check the longer language.
5. **No words the context supplies.** Inside the share menu, "share" and "mix" need not repeat on every entry;
   "session" adds nothing to "SoundCraft".

## Example: the share menu (0.11.4)

| Before | After (English) | After (German) |
|---|---|---|
| Share mix · As Play sounds, Ogg/Opus | Share OGG · As Play sounds, for messengers | OGG teilen · Wie Play, für Messenger |
| Mix as MP3 · As Play sounds, for any player | Share MP3 · As Play sounds, for any player | MP3 teilen · Wie Play, für jeden Player |
| Stems · Cleaned WAVs for a DAW (ZIP) | Stems · Cleaned WAVs for a DAW | Einzelspuren · Bereinigte WAVs für eine DAW |
| Back up project · Whole project as ZIP | Back up project · Export whole project | Projekt sichern · Ganzes Projekt exportieren |
| SoundCraft session · Tracks, positions and effects for SoundCraft (ZIP) | SoundCraft · Tracks, positions and effects | SoundCraft · Spuren, Positionen und Effekte |

## Workflow

For a new feature, propose its texts as a small table (German/English, title/subtitle) before implementing them, so the
whole screen can be judged at once. The guide may explain more; it follows its own style
([decisions](decisions.md), guide content).
