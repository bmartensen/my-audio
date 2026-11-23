package com.martensening.my_audio.data

import com.martensening.my_audio.audio.PcmWavInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A SoundCraft session (`.scraft` format version 1 with its `Audio Files/`, as a ZIP) as a new project, so it can
 * be continued on the phone and exported again with every position kept. The reverse of [SoundCraftSession]:
 * each audio track's active playlist becomes one take at its first clip, and the session's levels, pan, volume
 * automation and built-in EQ, compressor and reverb become My Audio settings. The session is hostile input:
 * sizes are bounded and media paths must stay inside the session's folder in the ZIP.
 */
object SoundCraftImport {
    private const val MAX_SESSION_BYTES = 32L * 1024 * 1024
    private const val MAX_TAKE_FRAMES = 48_000L * 60 * 30
    private const val RATE = 48_000
    private const val SILENT_DB = -144.0

    /** Whether a ZIP with these entry names holds a SoundCraft session (and no My Audio backup). */
    fun isSession(entryNames: List<String>) =
        ProjectRepository.PROJECT_FILE !in entryNames && entryNames.any { it.endsWith(".scraft", ignoreCase = true) }

    /**
     * Imports the session in [zip] into [repository]. [decode] converts one audio file into the internal 48 kHz
     * PCM16 WAV and returns its frames, like "Import track" on each platform. [work] is an empty scratch folder.
     */
    suspend fun import(zip: ZipFile, work: File, repository: ProjectRepository, nowMillis: Long, fallbackName: String,
                       decode: suspend (source: File, target: File) -> Long): Project? {
        val entry = zip.entries().toList().firstOrNull { !it.isDirectory && it.name.endsWith(".scraft", ignoreCase = true) } ?: return null
        if (entry.size > MAX_SESSION_BYTES) return null
        val session = zip.getInputStream(entry).use { Json.parseToJsonElement(it.readBytes().decodeToString()) } as? JsonObject ?: return null
        if ((session.long("format_version") ?: return null) > SoundCraftSession.FORMAT_VERSION) return null
        val sessionRate = (session.long("sample_rate") ?: RATE.toLong()).toInt().takeIf { it in 8000..384000 } ?: RATE
        fun frames(samples: Long) = if (sessionRate == RATE) samples else (samples.toDouble() * RATE / sessionRate).roundToLong()
        val folder = entry.name.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val sources = session.array("sources").mapNotNull { it as? JsonObject }.associateBy { it.long("id") }
        val notImported = mutableListOf<String>()
        val decoded = mutableMapOf<Long, Audio?>()

        suspend fun audioOf(id: Long): Audio? = decoded.getOrPut(id) {
            val path = sources[id]?.string("path") ?: return@getOrPut null
            // Only files below the session's folder; a path that leaves it or is absolute is never read.
            if (path.startsWith("/") || path.contains('\\') || path.split('/').any { it == ".." } || ':' in path) return@getOrPut null
            val media = zip.getEntry(folder + path) ?: return@getOrPut null
            val source = File(work, "source.${path.substringAfterLast('.', "wav").lowercase()}")
            val target = File(work, "source-$id.wav")
            try {
                zip.getInputStream(media).use { input -> source.outputStream().use { input.copyTo(it) } }
                // SoundCraft writes 32-bit float WAVs, which not every platform decoder reads; 48 kHz WAVs are read
                // here directly, everything else goes through the platform's import decoder.
                Audio.readWav48(source) ?: run {
                    decode(source, target)
                    Audio.read(target)
                }
            } catch (_: Exception) {
                null
            } finally {
                source.delete()
                target.delete()
            }
        }

        val takes = mutableListOf<ProjectRepository.ImportedTake>()
        var masterChain = emptyList<EffectSlot>()
        for ((index, track) in session.array("tracks").mapNotNull { it as? JsonObject }.withIndex()) {
            val name = track.string("name").orEmpty().ifBlank { "Track ${index + 1}" }
            val mixer = track.obj("mixer")
            when (track.string("kind")) {
                "Audio" -> Unit
                "Master" -> {
                    masterChain = effects(mixer, master = true, notImported).first
                    continue
                }
                else -> {
                    notImported += name
                    continue
                }
            }
            val playlists = track.array("playlists").mapNotNull { it as? JsonObject }
            val active = playlists.getOrNull((track.long("active_playlist") ?: 0).toInt()) ?: continue
            val clips = active.array("clips").mapNotNull { it as? JsonObject }
                .filter { it.bool("muted") != true }
                .mapNotNull { clip ->
                    val content = clip.obj("content")?.obj("Audio") ?: return@mapNotNull null
                    val audio = audioOf(content.long("source") ?: return@mapNotNull null) ?: run {
                        notImported += "$name: ${clip.string("name").orEmpty()}"
                        return@mapNotNull null
                    }
                    Clip(audio, frames(clip.long("start") ?: 0), frames(content.long("offset") ?: 0),
                        frames(clip.long("length") ?: 0), clip.double("gain_db") ?: 0.0,
                        frames(clip.obj("fade_in")?.long("len") ?: 0), frames(clip.obj("fade_out")?.long("len") ?: 0))
                }
                .filter { it.length > 0 }
            if (clips.isEmpty()) continue
            val stereo = track.string("format") == "Stereo" || clips.any { it.audio.channels == 2 }
            val first = clips.minOf { it.start }
            val end = min(clips.maxOf { it.start + it.length }, first + MAX_TAKE_FRAMES)
            val file = File(work, "${takes.size}.wav")
            writeTake(file, clips, first, end - first, if (stereo) 2 else 1)

            val (chain, compensationDb) = effects(mixer, master = false, notImported)
            val volumeDb = mixer?.double("volume_db") ?: 0.0
            val mono = !stereo
            val pans = mixer?.array("pan")?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }.orEmpty()
            val pan = if (mono) pans.firstOrNull() ?: 0.0 else if (pans.size >= 2) (pans[0] + pans[1]) / 2 else 0.0
            // Volume automation overrides the fader in SoundCraft: its loudest point becomes the track volume.
            val points = track.array("automation").mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("param") == "Volume" && it.bool("enabled") != false }
                ?.array("points")?.mapNotNull { it as? JsonObject }
                ?.mapNotNull { point -> point.long("at")?.let { frames(it) to (point.double("value") ?: SILENT_DB) } }
                .orEmpty()
            val baseDb = if (points.isEmpty()) volumeDb else points.maxOf { it.second }
            val ownDb = baseDb - (if (mono) SoundCraftSession.MONO_PAN_COMPENSATION_DB else 0.0) - compensationDb
            val envelope = VolumeEnvelope.sanitize(points.map { (at, value) ->
                VolumePoint(at, if (value <= SILENT_DB) 0f else 10.0.pow((value - baseDb) / 20).toFloat().coerceIn(0f, 1f))
            })
            val effects = TrackEffects.IMPORTED.copy(chain = chain)
            val take = ProjectRepository.ImportedTake(name, file, end - first, if (stereo) 2 else 1, offsetFrames = first) { created ->
                created.copy(
                    volume = 10.0.pow(ownDb / 20).toFloat().coerceIn(0f, ProjectRepository.MAX_IMPORTED_GAIN),
                    pan = pan.toFloat().coerceIn(-1f, 1f),
                    muted = mixer?.bool("mute") == true,
                    solo = mixer?.bool("solo") == true,
                    effects = effects,
                    volumeEnvelope = envelope
                )
            }
            takes += take
        }
        if (takes.isEmpty()) return null
        val bpm = session.obj("tempo")?.array("tempos")?.firstOrNull()?.let { it as? JsonObject }?.double("bpm")
        val notes = listOfNotNull(
            session.string("comments")?.takeIf { it.isNotBlank() },
            notImported.distinct().takeIf { it.isNotEmpty() }?.let { "Not imported from SoundCraft: " + it.joinToString(", ") }
        ).joinToString("\n\n")
        val name = session.string("name")?.takeIf { it.isNotBlank() } ?: fallbackName
        return repository.createFromTakes(name, RATE, takes, nowMillis) { project ->
            project.copy(
                masterChain = masterChain,
                notes = notes,
                metronome = bpm?.let { project.metronome.copy(bpm = it.roundToInt().coerceIn(Metronome.MIN_BPM, Metronome.MAX_BPM)) }
                    ?: project.metronome
            )
        }
    }

    /**
     * The session's built-in EQ, compressor and reverb inserts as My Audio slots (at most six), and the level (dB) the
     * export added on the channel for the reverb; other plugins are named in [notImported]. The master's maximizer
     * stands in for My Audio's own fixed master stage and is dropped.
     */
    private fun effects(mixer: JsonObject?, master: Boolean, notImported: MutableList<String>): Pair<List<EffectSlot>, Double> {
        val slots = mutableListOf<EffectSlot>()
        var compensationDb = 0.0
        for (insert in mixer?.array("inserts").orEmpty().mapNotNull { it as? JsonObject }) {
            val plugin = insert.string("plugin").orEmpty()
            val params = insert.obj("params")
            fun p(key: String, default: Double) = params?.double(key) ?: default
            val enabled = insert.bool("bypass") != true && insert.bool("active") != false
            val effect: ChainEffect? = when (plugin) {
                "eq_7band" -> ChainEffect.Tone(
                    EqEffect(true, p("low_shelf_gain", 0.0).db(), p("mid_gain", 0.0).db(), p("high_shelf_gain", 0.0).db()),
                    CompressorEffect(enabled = false)
                )
                "compressor" -> {
                    val compressor = CompressorEffect(true, ((-10.0 - p("threshold", -20.0)) / 20.0).toFloat().coerceIn(0f, 1f))
                    // The export splits one „EQ (My Audio)" into eq_7band + compressor; join them again.
                    val previous = slots.lastOrNull()
                    val tone = previous?.effect as? ChainEffect.Tone
                    if (tone != null && !tone.compressor.enabled && previous.enabled == enabled) {
                        slots[slots.lastIndex] = previous.copy(effect = tone.copy(compressor = compressor))
                        null
                    } else {
                        ChainEffect.Tone(EqEffect(enabled = false), compressor)
                    }
                }
                "room_reverb", "plate_reverb" -> {
                    val m = (p("mix", 25.0) / 100.0).coerceIn(0.0, 1.0)
                    val angle = if (m >= 1.0) PI / 2 else atan(m / (1.0 - m))
                    if (enabled && m < 1.0) compensationDb += 20.0 * log10(max(cos(angle), 1e-6) / (1.0 - m))
                    ChainEffect.Reverb(ReverbEffect(
                        seconds = p("decay", 1.5).toFloat().coerceIn(ReverbEffect.MIN_SECONDS, ReverbEffect.MAX_SECONDS),
                        preDelayMs = p("pre_delay", 20.0).toFloat().coerceIn(0f, ReverbEffect.MAX_PRE_DELAY_MS),
                        damping = ((16000.0 - p("damping", 6000.0)) / 14000.0).toFloat().coerceIn(0f, 1f),
                        mix = (angle / (PI / 2)).toFloat().coerceIn(0f, 1f)
                    ))
                }
                "maximizer" -> if (master) null else {
                    if (enabled) notImported += plugin
                    null
                }
                else -> {
                    if (enabled) notImported += plugin
                    null
                }
            }
            if (effect == null) continue
            if (slots.size == EffectSlot.MAX_PER_CHAIN) {
                notImported += plugin
                continue
            }
            slots += EffectSlot(effect, enabled)
        }
        return slots to compensationDb
    }

    private fun Double.db() = toFloat().coerceIn(-EqEffect.RANGE_DB, EqEffect.RANGE_DB)

    /** A decoded source file: interleaved samples at 48 kHz, mono or stereo. */
    private class Audio(val channels: Int, val samples: FloatArray) {
        val frames get() = samples.size / channels

        companion object {
            /** The internal PCM16 WAV the platform decoders write. */
            fun read(file: File): Audio {
                val info = PcmWavInfo.read(file)
                val bytes = ByteArray((info.frames * info.channels * 2).toInt())
                RandomAccessFile(file, "r").use { it.seek(info.dataOffset); it.readFully(bytes) }
                val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                return Audio(info.channels, FloatArray(shorts.remaining()) { shorts.get(it) / 32768f })
            }

            /**
             * A 48 kHz mono or stereo WAV with PCM 16/24/32-bit or 32-bit float samples, or null for anything else
             * (left to the platform decoder).
             */
            fun readWav48(file: File): Audio? {
                val bytes = file.readBytes()
                val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
                var format = 0
                var channels = 0
                var rate = 0
                var bits = 0
                var position = 12
                while (position + 8 <= bytes.size) {
                    val id = String(bytes, position, 4)
                    val size = data.getInt(position + 4).toLong() and 0xffffffffL
                    val body = position + 8
                    if (id == "fmt " && size >= 16) {
                        format = data.getShort(body).toInt() and 0xffff
                        channels = data.getShort(body + 2).toInt()
                        rate = data.getInt(body + 4)
                        bits = data.getShort(body + 14).toInt()
                        if (format == 0xFFFE && size >= 26) format = data.getShort(body + 24).toInt() and 0xffff
                    } else if (id == "data") {
                        if (rate != RATE || channels !in 1..2) return null
                        val bytesPerSample = bits / 8
                        val pcm = format == 1 && bits in setOf(16, 24, 32)
                        val float = format == 3 && bits == 32
                        if (!pcm && !float) return null
                        val count = (min(size, (bytes.size - body).toLong()) / bytesPerSample).toInt() / channels * channels
                        val samples = FloatArray(count) { index ->
                            val at = body + index * bytesPerSample
                            when {
                                float -> data.getFloat(at)
                                bits == 16 -> data.getShort(at) / 32768f
                                bits == 24 -> ((bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or
                                    (bytes[at + 2].toInt() shl 16)) / 8388608f
                                else -> data.getInt(at) / 2147483648f
                            }
                        }
                        return Audio(channels, samples)
                    }
                    position = body + size.toInt() + (size.toInt() and 1)
                }
                return null
            }
        }
    }

    /** One clip in project frames: [offset] into its source, gain and linear fades. */
    private class Clip(val audio: Audio, val start: Long, val offset: Long, val length: Long, val gainDb: Double,
                       val fadeIn: Long, val fadeOut: Long)

    /** Sums [clips] from project frame [first] into a PCM16 WAV with [channels]. */
    private fun writeTake(file: File, clips: List<Clip>, first: Long, frames: Long, channels: Int) {
        val mix = FloatArray((frames * channels).toInt())
        for (clip in clips) {
            val gain = 10.0.pow(clip.gainDb / 20).toFloat()
            for (i in 0 until clip.length) {
                val source = clip.offset + i
                if (source < 0 || source >= clip.audio.frames) continue
                val at = clip.start + i - first
                if (at < 0 || at >= frames) continue
                var g = gain
                if (clip.fadeIn > 0 && i < clip.fadeIn) g *= i.toFloat() / clip.fadeIn
                if (clip.fadeOut > 0 && i >= clip.length - clip.fadeOut) g *= (clip.length - i).toFloat() / clip.fadeOut
                val base = (source * clip.audio.channels).toInt()
                val left = clip.audio.samples[base]
                val right = if (clip.audio.channels == 2) clip.audio.samples[base + 1] else left
                if (channels == 1) {
                    mix[at.toInt()] += g * (left + right) / 2
                } else {
                    mix[(at * 2).toInt()] += g * left
                    mix[(at * 2 + 1).toInt()] += g * right
                }
            }
        }
        val data = ByteBuffer.allocate(mix.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (value in mix) data.putShort((value.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort()); putInt(RATE)
            putInt(RATE * channels * 2); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        file.outputStream().use { out ->
            out.write(header.array())
            out.write(data.array())
        }
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = (this[key] as? JsonArray).orEmpty()
    private fun JsonObject.primitive(key: String) = this[key] as? JsonPrimitive
    private fun JsonObject.string(key: String) = primitive(key)?.contentOrNull
    private fun JsonObject.long(key: String) = primitive(key)?.longOrNull ?: primitive(key)?.doubleOrNull?.toLong()
    private fun JsonObject.double(key: String) = primitive(key)?.doubleOrNull
    private fun JsonObject.bool(key: String) = primitive(key)?.booleanOrNull
}
