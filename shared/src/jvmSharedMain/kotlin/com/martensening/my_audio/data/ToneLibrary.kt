package com.martensening.my_audio.data

import com.martensening.my_audio.audio.NativeAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Kind of an imported tone; codes match `ToneKind` in `AmpCabinet.h`. */
enum class ToneKind(val code: Int, val extension: String) {
    AmpModel(1, "nam"),
    Cabinet(2, "wav");

    companion object {
        fun ofExtension(name: String): ToneKind? = when (name.substringAfterLast('.', "").lowercase()) {
            "nam" -> AmpModel
            "wav", "wave" -> Cabinet
            else -> null
        }
    }
}

/** One imported amp model or cabinet IR; [hash] is the SHA-256 of the file and its identity in projects. */
data class Tone(val hash: String, val kind: ToneKind, val name: String, val details: String = "")

/** What an import added; [failed] names the files that are no valid profile or IR. */
data class ToneImportResult(val added: List<Tone>, val duplicates: Int, val failed: List<String>) {
    val empty get() = added.isEmpty() && duplicates == 0
}

/**
 * The device's imported tones (amp models and cabinet IRs) in `tones/` next to the projects: each file once,
 * named by its content hash, plus `tones.json` with names. Files are never changed after import, so projects
 * and backups can refer to them by hash. Third-party files the user downloaded themselves; nothing is bundled.
 */
class ToneLibrary(private val dir: File) {
    private val index = File(dir, INDEX_FILE)
    private val _tones = MutableStateFlow(load())
    val tones: StateFlow<List<Tone>> = _tones.asStateFlow()
    private val handles = mutableMapOf<String, Int>()

    fun find(hash: String): Tone? = _tones.value.firstOrNull { it.hash == hash }

    fun file(tone: Tone) = File(dir, "${tone.hash}.${tone.kind.extension}")

    /** Native handle for effect chains, loading the file on first use; null when the tone is missing or broken. */
    @Synchronized
    fun handle(hash: String): Int? {
        handles[hash]?.let { return it.takeIf { handle -> handle >= 0 } }
        val tone = find(hash) ?: return null
        val handle = runCatching { NativeAudio.loadTone(file(tone).absolutePath, tone.kind.code) }.getOrDefault(-1)
        handles[hash] = handle
        return handle.takeIf { it >= 0 }
    }

    /**
     * Imports a downloaded profile (`.nam`), IR (`.wav`) or a ZIP of them, as TONE3000 delivers them. Every
     * profile and WAV inside a ZIP is imported; entry paths are only used as names, never as file paths. Blocks.
     */
    fun import(fileName: String, input: InputStream): ToneImportResult {
        val added = mutableListOf<Tone>()
        var duplicates = 0
        val failed = mutableListOf<String>()
        fun single(name: String, stream: InputStream) {
            when (val result = importSingle(name, stream)) {
                null -> failed += name
                else -> if (result.second) added += result.first else duplicates++
            }
        }
        val buffered = input.buffered()
        buffered.mark(4)
        val magic = ByteArray(4).also { buffered.read(it) }
        buffered.reset()
        if (magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || name.startsWith(".") || entry.name.startsWith("__MACOSX/")) continue
                    if (ToneKind.ofExtension(name) == null) continue
                    single(name, zip)
                }
            }
        } else {
            single(fileName, buffered)
        }
        return ToneImportResult(added, duplicates, failed)
    }

    /** Restores a tone from a project backup under its [hash]; [name] comes from the slot. Blocks. */
    fun restore(hash: String, kind: ToneKind, name: String, input: InputStream) {
        if (find(hash) != null) return
        importSingle("$name.${kind.extension}", input, kind)
    }

    /** Removes a tone; slots that use it stay in their projects and fall silent with a hint. */
    @Synchronized
    fun remove(hash: String) {
        val tone = find(hash) ?: return
        file(tone).delete()
        handles.remove(hash)
        save(_tones.value.filterNot { it.hash == hash })
    }

    /** The tone and whether it is new; null when the file is no usable profile or IR. */
    private fun importSingle(fileName: String, input: InputStream, knownKind: ToneKind? = null): Pair<Tone, Boolean>? {
        dir.mkdirs()
        val temp = File.createTempFile("import", ".tmp", dir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            temp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    size += read
                    if (size > MAX_FILE_BYTES) return null
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
            }
            val head = temp.inputStream().use { stream -> ByteArray(4).also { stream.read(it) } }
            val kind = knownKind ?: ToneKind.ofExtension(fileName) ?: when {
                head.decodeToString() == "RIFF" -> ToneKind.Cabinet
                head[0] == '{'.code.toByte() -> ToneKind.AmpModel
                else -> return null
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            synchronized(this) { find(hash)?.let { return it to false } }
            val info = runCatching { NativeAudio.inspectTone(temp.absolutePath, kind.code) }.getOrNull() ?: return null
            if (info.firstOrNull() != "1") return null
            val metadata = info.getOrNull(4).orEmpty()
            val tone = Tone(hash, kind, displayName(fileName, metadata), details(metadata))
            synchronized(this) {
                find(hash)?.let { return it to false }
                val target = file(tone)
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
                save(_tones.value + tone)
            }
            return tone to true
        } finally {
            temp.delete()
        }
    }

    private fun displayName(fileName: String, metadata: String): String {
        val fromMetadata = metadataField(metadata, "name")
        return fromMetadata?.takeIf { it.isNotBlank() } ?: fileName.substringAfterLast('/').substringBeforeLast('.')
    }

    /** Gear and author from NAM metadata, e.g. "Fender Super Reverb 1965 · amalgamaudio". */
    private fun details(metadata: String): String = listOfNotNull(
        metadataField(metadata, "gear_model") ?: metadataField(metadata, "gear_make"),
        metadataField(metadata, "modeled_by")
    ).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    private fun metadataField(metadata: String, key: String): String? = runCatching {
        Json.parseToJsonElement(metadata).jsonObject[key]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun load(): List<Tone> = runCatching {
        (Json.parseToJsonElement(index.readText()).jsonObject["tones"] as JsonArray).mapNotNull { element ->
            val tone = element as? JsonObject ?: return@mapNotNull null
            val hash = tone["hash"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val kind = ToneKind.entries.firstOrNull { it.code == tone["kind"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }
                ?: return@mapNotNull null
            Tone(hash, kind, tone["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                tone["details"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }.filter { File(dir, "${it.hash}.${it.kind.extension}").isFile }
    }.getOrDefault(emptyList())

    private fun save(tones: List<Tone>) {
        _tones.value = tones
        dir.mkdirs()
        val temp = File(dir, "$INDEX_FILE.tmp")
        temp.writeText(buildJsonObject {
            put("tones", buildJsonArray {
                for (tone in tones) add(buildJsonObject {
                    put("hash", tone.hash)
                    put("kind", tone.kind.code)
                    put("name", tone.name)
                    if (tone.details.isNotEmpty()) put("details", tone.details)
                })
            })
        }.toString())
        if (!temp.renameTo(index)) {
            index.delete()
            temp.renameTo(index)
        }
    }

    companion object {
        const val DIR = "tones"
        private const val INDEX_FILE = "tones.json"
        // Profiles are well below 1 MB and cabinet IRs a few hundred kB; anything far larger is no tone.
        private const val MAX_FILE_BYTES = 32L * 1024 * 1024

        @Volatile
        private var instance: ToneLibrary? = null

        /** The app-wide library below [root], next to the projects. */
        fun forRoot(root: File): ToneLibrary =
            instance ?: synchronized(this) { instance ?: ToneLibrary(File(root, DIR)).also { instance = it } }

        /** The library once [forRoot] has run; effect chains use it to find their tones. */
        val current: ToneLibrary? get() = instance

        /** Tones that the chains of [project] use, as hash to slot (for backups). */
        fun usedBy(project: Project): Map<String, ChainEffect> =
            (project.tracks.flatMap { it.effects.chain } + project.masterChain + project.recordTrack?.effects?.chain.orEmpty())
                .mapNotNull { slot ->
                    when (val effect = slot.effect) {
                        is ChainEffect.AmpModel -> effect.tone to effect
                        is ChainEffect.Cabinet -> effect.tone to effect
                        else -> null
                    }
                }.toMap()
    }
}
