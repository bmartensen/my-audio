package com.martensening.my_audio.data

/**
 * Finds chords in a song sheet written with chords above the lyrics. A line is a chord line
 * only when every word is a chord or a neutral sheet mark (bar line, repeat, label), so lyrics
 * stay untouched. English and German names are understood: B/Bb/H, Fis/Cis/Es/As/Des and
 * lowercase German minor chords such as a, h or fis.
 */
object ChordSheet {
    /** Character ranges of all chords in [text], in order. */
    fun chordRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var lineStart = 0
        for (line in text.split('\n')) {
            lineChords(line)?.forEach { ranges += (it.first + lineStart)..(it.last + lineStart) }
            lineStart += line.length + 1
        }
        return ranges
    }

    /** Ranges of the chords in [line], or null when it is not a chord line. */
    fun lineChords(line: String): List<IntRange>? {
        val words = WORD.findAll(line).toList()
        // A short label such as "Intro:" or "Strophe 1:" may lead the line.
        val labelEnd = words.take(MAX_LABEL_WORDS).indexOfFirst { it.value.length > 1 && it.value.endsWith(':') }
        val chords = mutableListOf<IntRange>()
        for (word in words.drop(labelEnd + 1)) {
            val found = chordsIn(word.value) ?: bracketedChords(word.value)
            when {
                found != null -> found.forEach { chords += (word.range.first + it.first)..(word.range.first + it.last) }
                // Any other text in brackets, e.g. "(Chorus)" or "[Strophe 2]", is a mark, not lyrics.
                NEUTRAL.matches(word.value) || ANNOTATION.matches(word.value) -> Unit
                else -> return null
            }
        }
        // A lone lowercase chord such as "a" or "e" is more likely a word than a German minor chord.
        if (chords.size == 1 && line[chords[0].first].isLowerCase()) return null
        return chords.takeIf { it.isNotEmpty() }
    }

    fun isChord(word: String) = UPPER_CHORD.matches(word) || LOWER_CHORD.matches(word)

    /**
     * A quick progression written as one word: chords chained by an arrow or dash ("Em→D→C",
     * "Em-D-C", "Em->Em/D") or by slashes ("Em/D/C", "D/F#m7"). "Em/D" stays a slash chord.
     */
    fun isProgression(word: String): Boolean {
        if (isChord(word)) return false
        val linked = word.split(LINK)
        if (linked.size >= 2 && linked.all(::isChord)) return true
        val slashed = word.split('/')
        // Two chords behind one slash only count when the second is no plain bass note
        // ("D/F#m7"); "Em/D" was already recognised as a slash chord above.
        return slashed.size >= 2 && slashed.all(::isChord)
    }

    /**
     * The chords inside [word]: usually one, possibly in brackets like "(C)"; or several joined
     * by brackets like "Am(C)". Brackets, bars and commas are never part of a chord.
     */
    private fun chordsIn(word: String): List<IntRange>? {
        chordIn(word)?.let { return listOf(it) }
        val parts = PART.findAll(word).toList()
        if (parts.size < 2) return null
        val chords = mutableListOf<IntRange>()
        for (part in parts) {
            when {
                isChord(part.value) -> chords += part.range
                NEUTRAL.matches(part.value) -> Unit
                else -> return null
            }
        }
        return chords.takeIf { it.isNotEmpty() }
    }

    /** Chords of a bracketed group with spaces such as "(C G)"; null if it holds anything else. */
    private fun bracketedChords(group: String): List<IntRange>? {
        if (!ANNOTATION.matches(group)) return null
        val chords = mutableListOf<IntRange>()
        for (word in WORD.findAll(group.substring(1, group.length - 1))) {
            val found = chordsIn(word.value)
            when {
                found != null -> found.forEach { chords += (word.range.first + 1 + it.first)..(word.range.first + 1 + it.last) }
                NEUTRAL.matches(word.value) -> Unit
                else -> return null
            }
        }
        return chords.takeIf { it.isNotEmpty() }
    }

    /** The chord inside [word]; a bracket, bar or comma attached to it is not part of the chord. */
    private fun chordIn(word: String): IntRange? {
        val starts = if (word.first() in OPENING) listOf(0, 1) else listOf(0)
        val ends = if (word.last() in CLOSING) listOf(word.length, word.length - 1) else listOf(word.length)
        for (start in starts) for (end in ends) {
            if (end > start && isChord(word.substring(start, end))) return start until end
        }
        // A progression is marked as a whole.
        for (start in starts) for (end in ends) {
            if (end > start && isProgression(word.substring(start, end))) return start until end
        }
        return null
    }

    private const val MAX_LABEL_WORDS = 3
    private const val OPENING = "([|"
    private const val CLOSING = ")]|,"
    private const val ACCIDENTAL = "[#b♯♭]"
    // German sharps end in -is, flats in -es or -s (As, Es). Lowercase roots are German minor
    // chords; lowercase -es/-as forms are left out because they are everyday German words.
    private const val UPPER_ROOT = "(?:[CDFGA]is|[CDG]es|[AE]s|[A-H]$ACCIDENTAL?)"
    private const val LOWER_ROOT = "(?:[cdfga]is|[a-h])"
    private const val QUALITY = "(?:min|maj|m|M|dim|°|ø|aug|\\+|-|Δ)"
    private const val EXTENSION_ITEM = "(?:maj\\d{0,2}|sus[24]?|add\\d{1,2}|alt|[#b♯♭+-]?\\d{1,2})"
    // Only extensions may stand in brackets, e.g. "F#m7(b5)"; "Am(C)" is two chords.
    private const val EXTENSION = "(?:$EXTENSION_ITEM|\\($EXTENSION_ITEM(?:[,/]?$EXTENSION_ITEM)*\\))"
    private const val BASS = "(?:/(?:$UPPER_ROOT|$LOWER_ROOT))"
    private val UPPER_CHORD = Regex("$UPPER_ROOT$QUALITY?$EXTENSION*$BASS?")
    private val LOWER_CHORD = Regex("$LOWER_ROOT$EXTENSION*$BASS?")
    /** Bar lines, repeat marks, "N.C.", "[Chorus]" and similar marks that may stand between chords. */
    // Dashes, dots and arrows such as "->", "=>", "→" or ">" between chords.
    private val NEUTRAL = Regex("\\|+:?|:?\\|+|[/\\-–—.…%*~<>=→←⇒]+|\\(?[x×]\\d+\\)?|\\(?\\d+[x×]\\)?|N\\.?C\\.?|\\[[^\\]]*]|[()]")
    /** Text completely in round or square brackets, also with spaces. */
    private val ANNOTATION = Regex("""\([^()]*\)|\[[^\[\]]*]""")
    // A bracketed group with spaces stays one word, so "(Chorus 2)" is read as a whole.
    private val LINK = Regex("->|→|>|–|—|-")
    private val WORD = Regex("""\([^()\s][^()]*\)(?=\s|$)|\[[^\[\]]*](?=\s|$)|\S+""")
    private val PART = Regex("[^()\\[\\]|,]+")
}
