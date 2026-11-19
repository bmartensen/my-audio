package com.martensening.my_audio.data

import org.junit.Assert.*
import org.junit.Test

class ChordSheetTest {
    private fun chords(line: String) = ChordSheet.lineChords(line)?.map { line.substring(it) }

    @Test fun recognisesEnglishChordLinesWithTabsAndSpacing() {
        assertEquals(listOf("E", "F#m", "A", "E"), chords("E   F#m   A   E"))
        assertEquals(listOf("D", "A", "F#m"), chords("\tD   A   F#m"))
        assertEquals(listOf("Am", "C/G", "F", "G7"), chords("  Am  C/G  F  G7"))
        assertEquals(listOf("Em7/B", "D/F#", "Am7/G", "H7/Dis"), chords("Em7/B  D/F#  Am7/G  H7/Dis"))
        assertEquals(
            listOf("Csus4", "Dadd9", "Gmaj7", "Ebm7b5", "F#m7(b5)", "A°", "Bbmaj7", "E5", "AM7", "C+", "Asus2"),
            chords("Csus4 Dadd9 Gmaj7 Ebm7b5 F#m7(b5) A° Bbmaj7 E5 AM7 C+ Asus2")
        )
    }

    @Test fun recognisesGermanChordNames() {
        assertEquals(listOf("H", "B", "Bb", "Bm", "b", "H7", "Hm"), chords("H  B  Bb  Bm  b  H7  Hm"))
        assertEquals(listOf("Fis", "Cis", "As", "Es", "Des", "Ges", "Gis7"), chords("Fis Cis As Es Des Ges Gis7"))
        assertEquals(listOf("a", "e", "h", "fis", "cis", "d7", "C/H"), chords("a  e  h  fis  cis  d7  C/H"))
    }

    @Test fun keepsLabelsBarsAndRepeatsOutOfTheChords() {
        assertEquals(listOf("C", "G", "Am", "F"), chords("Intro: C | G | Am | F |"))
        assertEquals(listOf("E", "A"), chords("Strophe 1: E - A (x2)"))
        assertEquals(listOf("G", "D"), chords("[Refrain] |: G D :| 2x N.C."))
        assertEquals(listOf("E", "A"), chords("(E) [A]"))
    }

    @Test fun treatsBracketedTextAsSheetMark() {
        assertEquals(listOf("F#m", "G", "F#m", "G", "F#m", "G"), chords("(Chorus)    F#m    G F#m    G F#m   G"))
        assertEquals(listOf("C", "G"), chords("[Strophe 2]  C  G"))
        assertEquals(listOf("Am", "F"), chords("(Chorus 2)  Am  F  (x2)"))
        assertEquals(listOf("C", "G", "Am"), chords("(C G)  Am"))
        assertEquals(listOf("D", "A"), chords("D  A  (leise)"))
        assertNull(chords("(Chorus)"))
        assertNull(chords("(Ooh yeah) baby"))
        assertNull(chords("(Ich weiß nicht) Es war einmal"))
        assertNull(chords("(Ooh) a"))
    }

    @Test fun marksQuickProgressionsAsOneWord() {
        assertEquals(listOf("H7", "Em/D/C"), chords(" H7                    Em/D/C"))
        assertEquals(listOf("Am", "H7", "Em/D/C"), chords(" Am      H7       Em/D/C"))
        assertEquals(listOf("Em→D→C", "Em-D-C", "Em->Em/D"), chords("Em→D→C  Em-D-C  Em->Em/D"))
        assertEquals(listOf("Em/D/C"), chords("(Em/D/C)"))
        assertEquals(listOf("D/F#m7"), chords("                        D/F#m7"))
        assertTrue(ChordSheet.isProgression("D/F#m7"))
        assertEquals(listOf("Em", "G", "Hm", "F#m"), chords("Em -> G      Hm      F#m    [2x]"))
        assertEquals(listOf("Am", "F", "C", "G"), chords("Am => F → C > G"))
        assertTrue(ChordSheet.isProgression("Em/D/C"))
        assertTrue(ChordSheet.isProgression("Em→Em/D→Em/C"))
        assertFalse(ChordSheet.isProgression("Em/D"))
        assertFalse(ChordSheet.isProgression("Am7-5"))
        assertFalse(ChordSheet.isProgression("Em"))
        assertNull(chords("Em/Dur/C"))
        assertNull(chords("Hin-und-her"))
    }

    @Test fun findsChordsInBrackets() {
        assertEquals(listOf("C", "Cadd2", "Em"), chords("           (C)          (Cadd2)          (Em)"))
        assertEquals(listOf("Am", "C", "G", "D"), chords("Am(C)   G   (D)"))
        assertEquals(listOf("F#m7(b5)", "E7(#9)", "A"), chords("F#m7(b5)  E7(#9)  A(x2)"))
        assertNull(chords("E|----0-----0---0-0-0---0---"))
        assertNull(chords("B|-3--------1---1-1---3"))
        assertNull(chords("Then(we) go"))
    }

    @Test fun leavesLyricsAndPlainTextAlone() {
        for (line in listOf(
            "Song title - Artist", "A boat on the river", "Am Abend geht die Sonne unter",
            "Es war einmal", "Go on", "Dim the lights", "Be a friend", "Add more salt", "Oh      So far away",
            "es as des", "his", "Hmm", "[Chorus]", "---", "", "   ", "a", "  e", "Let the golden age begin a"
        )) assertNull(line, ChordSheet.lineChords(line))
    }

    @Test fun singleUppercaseChordStaysAChordLine() {
        assertEquals(listOf("A"), chords("A"))
        assertEquals(listOf("a", "e"), chords("a e"))
    }

    @Test fun reportsRangesAcrossTheWholeText() {
        val text = "Title\nE   A\r\nlyric line\n\tH7"
        assertEquals(listOf("E", "A", "H7"), ChordSheet.chordRanges(text).map { text.substring(it) })
    }
}
