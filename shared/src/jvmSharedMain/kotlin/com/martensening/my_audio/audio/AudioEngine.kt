package com.martensening.my_audio.audio

import com.martensening.my_audio.data.ChainEffect
import com.martensening.my_audio.data.ToneLibrary
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.data.Metronome
import com.martensening.my_audio.data.PlaybackLoop
import com.martensening.my_audio.data.Project
import com.martensening.my_audio.data.ProjectRepository
import com.martensening.my_audio.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kotlin facade of the native multitrack engine. Serialises engine calls and renders the project
 * mix (including all track effects) ahead of playback or recording, so stream start only needs
 * a prepared mix and the takes heard while recording already sound like the final mix.
 */
object AudioEngine {
    private val lock = Mutex()
    private var preparedMix: Triple<Long, List<Track>, List<EffectSlot>>? = null

    /** Renders the mix of the audible tracks (solo, else unmuted) unless the same tracks are already prepared. */
    suspend fun prepare(project: Project, repository: ProjectRepository) = lock.withLock {
        val key = Triple(project.id, project.tracks, project.masterChain)
        if (preparedMix == key) return@withLock
        withContext(Dispatchers.Default) {
            val tracks = TrackArguments(project, project.audibleTracks, repository)
            check(NativeAudio.setTracks(tracks.paths, tracks.frames, tracks.parameters, tracks.chains,
                    tracks.envelopes, tracks.gains, encodeChain(project.masterChain))) {
                "Could not prepare project mix"
            }
        }
        preparedMix = key
    }

    /**
     * Writes every track (also muted ones) to [targets] as a mono/stereo WAV from the project start:
     * click and bleed removed as set for the track, latency compensated, no effects, volume or pan.
     */
    suspend fun exportStems(project: Project, repository: ProjectRepository, targets: List<File>): Boolean =
        lock.withLock {
            withContext(Dispatchers.Default) {
                val tracks = TrackArguments(project, project.tracks, repository)
                NativeAudio.exportStems(tracks.paths, tracks.frames, tracks.parameters, tracks.chains, tracks.envelopes,
                    tracks.gains, project.lengthFrames, targets.map { it.absolutePath }.toTypedArray())
            }
        }

    /**
     * DAW session export of all tracks: dry takes to [dry], takes through [renderChains] to [wet] where a chain is
     * not empty. Per track start frame, dry frames, rendered frames and channels; null on a write error.
     */
    suspend fun exportDawTracks(project: Project, repository: ProjectRepository, renderChains: List<List<EffectSlot>>,
                                dry: List<File>, wet: List<File>): LongArray? =
        lock.withLock {
            withContext(Dispatchers.Default) {
                val tracks = TrackArguments(project, project.tracks, repository)
                NativeAudio.exportDawTracks(tracks.paths, tracks.frames, tracks.parameters, tracks.chains, tracks.envelopes,
                    tracks.gains, renderChains.map(::encodeChain).toTypedArray(),
                    dry.map { it.absolutePath }.toTypedArray(), wet.map { it.absolutePath }.toTypedArray())
            }
        }

    /** Loudness (LUFS) of the audible mix through the master chain, before the fixed master stage. */
    suspend fun mixLoudness(project: Project, repository: ProjectRepository): Double =
        lock.withLock {
            if (project.audibleTracks.isEmpty()) return@withLock Double.NEGATIVE_INFINITY
            withContext(Dispatchers.Default) {
                val tracks = TrackArguments(project, project.audibleTracks, repository)
                NativeAudio.mixLoudness(tracks.paths, tracks.frames, tracks.parameters, tracks.chains, tracks.envelopes,
                    tracks.gains, encodeChain(project.masterChain))
            }
        }

    /**
     * Effect preview source: [frames] of [track] from project frame [startFrame], cleaned and
     * normalised as the mix does, before its effects. False if the take cannot be read.
     */
    suspend fun previewTrack(project: Project, track: Track, repository: ProjectRepository, startFrame: Long, frames: Long): Boolean =
        lock.withLock {
            withContext(Dispatchers.Default) {
                val tracks = TrackArguments(project, listOf(track), repository)
                NativeAudio.previewTrack(tracks.paths, tracks.frames, tracks.parameters, tracks.chains, tracks.envelopes,
                    tracks.gains, startFrame, frames)
            }
        }

    /** Effect preview source for the master chain: the audible tracks summed, from [startFrame]. */
    suspend fun previewMix(project: Project, repository: ProjectRepository, startFrame: Long, frames: Long): Boolean =
        lock.withLock {
            if (project.audibleTracks.isEmpty()) return@withLock false
            withContext(Dispatchers.Default) {
                val tracks = TrackArguments(project, project.audibleTracks, repository)
                NativeAudio.previewMix(tracks.paths, tracks.frames, tracks.parameters, tracks.chains, tracks.envelopes,
                    tracks.gains, startFrame, frames)
            }
        }

    /** Effect preview source: interleaved samples at [NativeAudio.SAMPLE_RATE]. */
    suspend fun previewSamples(samples: FloatArray, channels: Int): Boolean =
        withContext(Dispatchers.Default) { NativeAudio.previewSamples(samples, channels) }

    /** The preview source through [chain] as a seamless interleaved stereo loop; empty without a source. */
    suspend fun previewRender(chain: List<EffectSlot>): FloatArray =
        withContext(Dispatchers.Default) { NativeAudio.previewRender(encodeChain(chain)) }

    /** Native arguments for [tracks]; layout must match `readSources` in `JniBridge.cpp`. */
    private class TrackArguments(project: Project, tracks: List<Track>, repository: ProjectRepository) {
        val paths = tracks.flatMap { trackPaths(project, it, repository) }.toTypedArray()
        val frames = tracks.flatMap { listOf(it.offsetFrames, it.countInFrames) }.toLongArray()
        val parameters = tracks.flatMap(::processingParameters).toFloatArray()
        val chains = tracks.map { encodeChain(it.effects.chain) }.toTypedArray()
        val envelopes = tracks.map { track -> track.volumeEnvelope.map { it.frame }.toLongArray() }.toTypedArray()
        val gains = tracks.map { track -> track.volumeEnvelope.map { it.gain }.toFloatArray() }.toTypedArray()
    }

    /** Effect type codes of the native chain; must match `EffectType` in `ProjectMix.h`. */
    private const val EFFECT_TONE = 1f
    private const val EFFECT_REVERB = 2f
    private const val EFFECT_AIRWINDOWS = 3f
    private const val EFFECT_AIRWINDOWS_LEVEL_MATCHED = 4f
    private const val EFFECT_AMP_MODEL = 5f
    private const val EFFECT_AMP_MODEL_LEVEL_MATCHED = 6f
    private const val EFFECT_CABINET = 7f
    private const val EFFECT_CABINET_LEVEL_MATCHED = 8f

    /**
     * The audible slots of [chain] in order, each as type code, parameter count and parameters. Switched-off
     * slots and switched-off parts are left out, so they cost no rendering time; so are Airwindows effects this
     * build does not know and tones missing from the tone library. Layout see `ProjectMix.h`.
     */
    internal fun encodeChain(chain: List<EffectSlot>): FloatArray = chain.filter { it.audible }.flatMap { slot ->
        val (type, values) = when (val effect = slot.effect) {
            is ChainEffect.Tone -> EFFECT_TONE to listOf(
                if (effect.eq.enabled) effect.eq.lowDb else Float.NaN,
                effect.eq.presenceDb,
                effect.eq.highDb,
                if (effect.compressor.enabled) effect.compressor.amount else Float.NaN
            )
            is ChainEffect.Reverb -> EFFECT_REVERB to effect.reverb.let { listOf(it.seconds, it.preDelayMs, it.damping, it.mix) }
            is ChainEffect.Airwindows -> {
                val known = AirwindowsCatalog.find(effect.name) ?: return@flatMap emptyList()
                (if (effect.levelMatch) EFFECT_AIRWINDOWS_LEVEL_MATCHED else EFFECT_AIRWINDOWS) to
                    listOf(known.index.toFloat()) + AirwindowsCatalog.complete(effect.name, effect.values)
            }
            is ChainEffect.AmpModel -> {
                val handle = ToneLibrary.current?.handle(effect.tone) ?: return@flatMap emptyList()
                (if (effect.levelMatch) EFFECT_AMP_MODEL_LEVEL_MATCHED else EFFECT_AMP_MODEL) to
                    listOf(handle.toFloat(), effect.inputDb, effect.outputDb)
            }
            is ChainEffect.Cabinet -> {
                val handle = ToneLibrary.current?.handle(effect.tone) ?: return@flatMap emptyList()
                (if (effect.levelMatch) EFFECT_CABINET_LEVEL_MATCHED else EFFECT_CABINET) to listOf(handle.toFloat())
            }
            // Never audible; filtered above, listed for completeness.
            is ChainEffect.Unknown -> return@flatMap emptyList()
        }
        listOf(type, values.size.toFloat()) + values
    }.toFloatArray()

    /** Take, reference ("" = keep the bleed) and clean cache ("" = none); see `JniBridge.cpp`. */
    private fun trackPaths(project: Project, track: Track, repository: ProjectRepository): List<String> {
        val reference = repository.referenceFile(project.id, track)
            ?.takeIf { track.effects.removeBleed && it.isFile }
        // The cache holds click and bleed removed together, so it only fits when both apply.
        val clickRemovalApplies = track.recordedMetronome?.enabled != true || track.effects.removeMetronome
        val cache = repository.cleanCacheFile(project.id, track).takeIf { reference != null && clickRemovalApplies }
        return listOf(
            repository.trackFile(project.id, track).absolutePath,
            reference?.absolutePath.orEmpty(),
            cache?.absolutePath.orEmpty()
        )
    }

    /** Five values per track; layout must match `JniBridge.cpp` / `TrackSource`. */
    private fun processingParameters(track: Track): List<Float> {
        val effects = track.effects
        val click = track.recordedMetronome?.takeIf { effects.removeMetronome && it.enabled }
        return listOf(
            track.volume,
            click?.bpm?.toFloat() ?: 0f,
            click?.volume ?: 0f,
            if (effects.normalize.enabled) effects.normalize.targetDb else Float.NaN,
            track.pan
        )
    }

    /**
     * The click sounds while [recording]; during playback only if the project asks for it.
     * Takes effect immediately, also while a stream runs.
     */
    fun applyMetronome(metronome: Metronome, recording: Boolean) {
        val audible = metronome.enabled && (recording || metronome.duringPlayback)
        NativeAudio.setMetronome(audible, metronome.bpm.toFloat(), metronome.volume)
    }

    suspend fun startPlayback(route: TransportRoute): Boolean = lock.withLock {
        withContext(Dispatchers.Default) {
            NativeAudio.startPlayback(route.outputId, route.outputBluetooth)
        }
    }

    /** Continues a running playback at [frame]. */
    fun seekPlayback(frame: Long) = NativeAudio.seekPlayback(frame)

    /** Practice loop for playback, also while it runs; null switches it off. */
    fun applyLoop(loop: PlaybackLoop?) =
        if (loop != null) NativeAudio.setLoop(loop.startFrames, loop.endFrames) else NativeAudio.setLoop(0, 0)

    /**
     * Writes exactly what Play would output (audible tracks with their effects, plus the click if
     * the metronome is audible during playback) to [target] as Ogg/Opus, encoded by [encode].
     */
    suspend fun exportPlayback(
        project: Project, repository: ProjectRepository, target: File,
        encode: suspend (samples: FloatArray, sampleRate: Int, channels: Int, target: File) -> Unit
    ): Boolean {
        prepare(project, repository)
        val output = lock.withLock {
            applyMetronome(project.metronome, recording = false)
            withContext(Dispatchers.Default) { NativeAudio.renderedMix() }
        }
        if (output.isEmpty()) return false
        // The encoder drops the last partial Opus frame; 100 ms of silence keep the true ending.
        val padded = output.copyOf(output.size + NativeAudio.SAMPLE_RATE / 10 * MIX_CHANNELS)
        withContext(Dispatchers.Default) { encode(padded, NativeAudio.SAMPLE_RATE, MIX_CHANNELS, target) }
        return true
    }

    /** Records [file]; if something is played, it is captured to [referenceFile] for the bleed removal. */
    suspend fun startRecording(file: File, referenceFile: File, countInFrames: Long, route: TransportRoute): Boolean =
        lock.withLock {
            if (!route.ready) return@withLock false
            withContext(Dispatchers.Default) {
                NativeAudio.startRecording(file.absolutePath, referenceFile.absolutePath, countInFrames,
                    route.outputId, route.inputId, route.communication, route.bluetooth,
                    route.inputChannel?.code ?: InputChannel.DEVICE_CODE)
            }
        }

    private val monitorLock = Mutex()

    /**
     * What the microphone is monitored through while recording or in live audition: [chain] (empty = dry),
     * then [volume] and [pan]; null monitors nothing. Never changes what is recorded: takes stay dry.
     */
    suspend fun setMonitor(chain: List<EffectSlot>?, volume: Float = 1f, pan: Float = 0f) = monitorLock.withLock {
        withContext(Dispatchers.Default) {
            NativeAudio.setMonitorChain(encodeChain(chain.orEmpty()), volume, pan, chain != null)
        }
    }

    /** Live audition on [route]: microphone through the monitor chain to the output; [stop] ends it. */
    suspend fun startMonitoring(route: TransportRoute): Boolean = lock.withLock {
        if (!route.ready) return@withLock false
        withContext(Dispatchers.Default) {
            NativeAudio.startMonitoring(route.outputId, route.inputId, route.communication, route.bluetooth,
                route.inputChannel?.code ?: InputChannel.DEVICE_CODE)
        }
    }

    /** Stops the running stream; returns the recorded frames, or -1 when nothing was recorded. */
    suspend fun stop(): Long = lock.withLock { withContext(Dispatchers.Default) { NativeAudio.stop() } }

    /** Blocking stop for teardown paths that cannot suspend. */
    fun stopNow(): Long = NativeAudio.stop()

    val isActive get() = NativeAudio.isActive()

    /** The rendered mix is interleaved stereo. */
    private const val MIX_CHANNELS = 2
    val positionFrames get() = NativeAudio.positionFrames()
    fun takeInputPeak() = NativeAudio.takeInputPeak()
}
