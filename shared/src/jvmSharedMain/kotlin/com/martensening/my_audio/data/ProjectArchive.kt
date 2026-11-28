package com.martensening.my_audio.data

import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** File names and ZIP files for sharing: mix, stems, project backups and SoundCraft sessions. */
object ProjectArchive {
    /** What "Share" hands out; each has its own file name suffix and MIME type. */
    enum class Kind(val suffix: String, val extension: String, val mimeType: String) {
        Mix("", "ogg", "audio/ogg"),
        Mp3("", "mp3", "audio/mpeg"),
        Stems(" - Spuren", "zip", "application/zip"),
        Backup(" - Projekt", "zip", "application/zip"),
        SoundCraft(" - SoundCraft", "zip", "application/zip")
    }

    /** Characters other apps stumble over become "_"; an empty result falls back to "My Audio". */
    fun safeName(name: String) = name.replace(Regex("""[^\p{L}\p{N} ._-]"""), "_").trim().ifEmpty { "My Audio" }

    fun fileName(projectName: String, kind: Kind) = "${safeName(projectName)}${kind.suffix}.${kind.extension}"

    /** "01 Gesang.wav", … in track order, so a DAW lists the stems as in the app. */
    fun stemNames(tracks: List<Track>): List<String> {
        val digits = tracks.size.toString().length.coerceAtLeast(2)
        return tracks.mapIndexed { index, track ->
            "${(index + 1).toString().padStart(digits, '0')} ${safeName(track.name)}.wav"
        }
    }

    /** Audio files a ZIP without `project.json` turns into tracks; decoded like "Import track". */
    private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "oga", "opus", "wav")

    /** A leading one- or two-digit track number, e.g. "0 Lead Vocals", "01 Gesang", "3 - Bass", "4_Synth". */
    private val TRACK_NUMBER = Regex("""^(\d{1,2})(?:\s*[-_.)]\s*|\s+)(\S.*)$""")

    /**
     * The audio files among the ZIP [entryNames] that become tracks, in track order: by their
     * leading number first, then by name. Folders, `__MACOSX/` and hidden files are skipped.
     */
    fun audioEntries(entryNames: List<String>): List<String> =
        entryNames
            .filter { name ->
                val parts = name.split('/')
                !name.endsWith('/') && parts.none { it == "__MACOSX" } && !parts.last().startsWith('.') &&
                    parts.last().substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
            }
            .sortedWith(compareBy<String>({ trackNumber(it) ?: Int.MAX_VALUE }, { baseName(it).lowercase() }, { it }))

    /** Track name for an audio entry: file name without folder, extension and leading track number. */
    fun trackName(entryName: String): String? {
        val base = baseName(entryName).trim()
        return (TRACK_NUMBER.find(base)?.groupValues?.get(2)?.trim() ?: base).ifEmpty { null }
    }

    private fun trackNumber(entryName: String) = TRACK_NUMBER.find(baseName(entryName).trim())?.groupValues?.get(1)?.toInt()

    private fun baseName(entryName: String) = entryName.substringAfterLast('/').substringBeforeLast('.')

    /** Writes [entries] (path in the archive to file); fast compression, WAVs hardly shrink anyway. */
    fun writeZip(target: File, entries: List<Pair<String, File>>) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            for ((name, file) in entries) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
