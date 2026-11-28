# Interface text

The interface stays clean, elegant and sparse: a label says what a control does, nothing more. Details live in the
user guide, and only when they really matter. These rules apply to every label, menu entry, subtitle, dialog and hint,
in every interface language alike. This page belongs to the [harness](../.agents/harness.md); a project without a user
interface can ignore it.

## Checklist

Draft all siblings of a menu, list or dialog together, in every interface language, before building any of them, and
check:

1. **One pattern for siblings.** Entries of the same kind share one grammatical form, e.g. verb + object for actions
   ("Export PDF", "Export CSV"; "PDF exportieren", "CSV exportieren"). All languages follow the same pattern.
2. **The subtitle adds, never repeats.** It says what the title leaves open, usually the purpose ("for printing",
   "for spreadsheets"), never the title again ("Export PDF" with "… as a PDF file").
3. **No technical details.** No file formats or containers when the title already implies them, codecs, encodings,
   library names, parentheses or annotations. If a detail matters, it goes into the guide.
4. **One line.** A subtitle fits one line at the narrowest supported width (about 30 characters on a phone); check the
   longest language.
5. **No words the context supplies.** Inside an export menu, "export" need not repeat on every entry if the menu title
   already says it.

## Example: an export menu

| Before | After (English) | After (German) |
|---|---|---|
| Export · Save as PDF document (A4) | Export PDF · For printing | PDF exportieren · Zum Drucken |
| CSV · Comma-separated values for Excel | Export CSV · For spreadsheets | CSV exportieren · Für Tabellen |
| Backup · Whole project as ZIP archive | Back up project · Everything in one file | Projekt sichern · Alles in einer Datei |

## Workflow

For a new feature, propose its texts as a small table (every language, title/subtitle) before implementing them, so the
whole screen can be judged at once. The user guide may explain more; it describes current behavior and how to use it,
never earlier behavior or design rationale.
