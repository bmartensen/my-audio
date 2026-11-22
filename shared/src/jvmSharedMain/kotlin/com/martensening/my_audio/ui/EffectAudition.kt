package com.martensening.my_audio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.audio.AudioEngine
import com.martensening.my_audio.audio.LoopPlayer
import com.martensening.my_audio.data.EffectSlot
import com.martensening.my_audio.platform.Log
import com.martensening.my_audio.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** What an effect is auditioned with; see [EffectAudition]. */
enum class AuditionSourceKind { Track, Mix, Sample }

/**
 * Prepares the native preview source (`AudioEngine.preview*`) for auditioning. Returns the kind of
 * source it prepared, or null when it has nothing to play; the bundled sample then stands in.
 */
interface AuditionSource {
    suspend fun prepare(): AuditionSourceKind?

    /** Called when an audition starts, e.g. to stop the project playback. */
    fun onStart() {}
}

/** Only the bundled sample (effect library, projects without recordings). */
object SampleAuditionSource : AuditionSource {
    override suspend fun prepare(): AuditionSourceKind? = null
}

/**
 * Auditions effects: plays a short excerpt through the chain in a loop, with an A/B switch "with /
 * without this effect". Changes are rendered again after a short pause and replace the playing
 * loop at the same position. Only one item ([playing]) sounds at a time.
 */
class EffectAudition(private val scope: CoroutineScope, private val source: AuditionSource) {
    /** Key of what is playing (a slot or a library effect), null when silent. */
    var playing by mutableStateOf<Any?>(null)
        private set

    /** True while the comparison "without this effect" sounds. */
    var bypassed by mutableStateOf(false)
        private set

    /** What is heard, once the first loop is rendered. */
    var sourceKind by mutableStateOf<AuditionSourceKind?>(null)
        private set

    /** Key of what is heard live (the microphone through its chain), null when Live is off. */
    var live by mutableStateOf<Any?>(null)
        private set

    /** Set while a Live start waits for the user to accept this warning. */
    var pendingWarning by mutableStateOf<LiveWarning?>(null)
        private set

    /** Why the last Live start failed, until the next attempt. */
    var liveMessage by mutableStateOf<StringResource?>(null)
        private set

    private val player = LoopPlayer()
    private var job: Job? = null
    private var loops: Pair<FloatArray, FloatArray>? = null
    private var liveJob: Job? = null
    private var liveChains: Pair<List<EffectSlot>, List<EffectSlot>>? = null
    private var pendingLive: Triple<Any, List<EffectSlot>, List<EffectSlot>>? = null

    /** Starts [key] with the chain [with] (A) and [without] (B), or stops it if it plays. */
    fun toggle(key: Any, with: List<EffectSlot>, without: List<EffectSlot>) {
        if (playing == key) {
            stop()
            return
        }
        stopLive()
        playing = key
        bypassed = false
        loops = null
        source.onStart()
        render(key, with, without, prepare = true)
    }

    /**
     * Live for [key]: the microphone through [with] (A) or [without] (B) to the output, or off if it runs.
     * Warns first where the output would feed back or lag; nothing is recorded.
     */
    fun toggleLive(key: Any, with: List<EffectSlot>, without: List<EffectSlot>) {
        if (live == key) {
            stopLive()
            return
        }
        val input = LiveInputs.current ?: return
        stopClip()
        val warning = input.warning()
        if (warning != null) {
            pendingLive = Triple(key, with, without)
            pendingWarning = warning
            return
        }
        startLive(key, with, without)
    }

    fun confirmLiveWarning() {
        pendingWarning = null
        pendingLive?.let { (key, with, without) -> startLive(key, with, without) }
        pendingLive = null
    }

    fun dismissLiveWarning() {
        pendingWarning = null
        pendingLive = null
    }

    /** New settings for the playing [key]; heard after a short pause, so dragging a slider stays smooth. */
    fun update(key: Any, with: List<EffectSlot>, without: List<EffectSlot>) {
        if (playing == key) render(key, with, without, prepare = false)
        if (live == key) {
            liveChains = with to without
            liveJob?.cancel()
            liveJob = liveScope.launch {
                delay(UPDATE_DELAY_MILLIS)
                liveLock.withLock { if (live == key) AudioEngine.setMonitor(if (bypassed) without else with) }
            }
        }
    }

    fun setBypass(value: Boolean) {
        bypassed = value
        loops?.let { player.play(if (value) it.second else it.first) }
        liveChains?.takeIf { live != null }?.let { (with, without) ->
            liveScope.launch { liveLock.withLock { AudioEngine.setMonitor(if (value) without else with) } }
        }
    }

    fun stop() {
        stopClip()
        stopLive()
    }

    private fun stopClip() {
        job?.cancel()
        job = null
        player.stop()
        playing = null
        bypassed = false
        loops = null
    }

    private fun startLive(key: Any, with: List<EffectSlot>, without: List<EffectSlot>) {
        val input = LiveInputs.current ?: return
        live = key
        bypassed = false
        liveChains = with to without
        liveMessage = null
        source.onStart()
        liveJob?.cancel()
        // Not the screen's scope: a dialog closing mid-start must still be able to stop the streams.
        liveScope.launch {
            liveLock.withLock {
                if (live != key) return@withLock
                AudioEngine.setMonitor(with)
                val result = input.start()
                if (result != LiveStart.Started && live == key) {
                    live = null
                    liveChains = null
                    AudioEngine.setMonitor(null)
                    liveMessage = if (result == LiveStart.NoPermission) Res.string.live_no_permission else Res.string.live_failed
                }
            }
        }
    }

    private fun stopLive() {
        if (live == null) return
        live = null
        liveChains = null
        liveJob?.cancel()
        liveJob = null
        val input = LiveInputs.current ?: return
        liveScope.launch { liveLock.withLock { input.stop() } }
    }

    private fun render(key: Any, with: List<EffectSlot>, without: List<EffectSlot>, prepare: Boolean) {
        job?.cancel()
        job = scope.launch {
            if (!prepare) delay(UPDATE_DELAY_MILLIS)
            if (prepare) {
                val kind = prepareSource() ?: run {
                    stop()
                    return@launch
                }
                sourceKind = kind
            }
            val a = AudioEngine.previewRender(with)
            val b = if (without == with) a else AudioEngine.previewRender(without)
            if (playing != key) return@launch
            if (a.isEmpty()) {
                stop()
                return@launch
            }
            loops = a to b
            player.play(if (bypassed) b else a)
        }
    }

    /** The project's own sound if there is any, else the bundled sample. */
    private suspend fun prepareSource(): AuditionSourceKind? {
        val kind = runCatching { source.prepare() }.getOrNull()
        // A silent excerpt (e.g. a loop where the track does not play) is no use for listening.
        if (kind != null && !isSilent(AudioEngine.previewRender(emptyList()))) return kind
        return if (SampleClip.prepare()) AuditionSourceKind.Sample else null
    }

    private fun isSilent(loop: FloatArray) = loop.none { abs(it) > SILENCE }

    private companion object {
        /** Live start/stop outlive the screen that asked for them; [liveLock] keeps them in tap order. */
        val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val liveLock = Mutex()
        const val UPDATE_DELAY_MILLIS = 150L
        const val SILENCE = 0.0005f   // about -66 dBFS
    }
}

/** The bundled clip "Short Jazzy Tune No.1" by painted-panda (freesound.org, CC0), see LICENSE. */
private object SampleClip {
    private const val PATH = "files/preview/short-jazzy-tune.wav"
    private var cached: Pair<FloatArray, Int>? = null

    @OptIn(ExperimentalResourceApi::class)
    suspend fun prepare(): Boolean {
        val clip = cached ?: runCatching { decodeWav(Res.readBytes(PATH)) }
            .onFailure { Log.w("EffectAudition", "Sample clip unreadable: ${it.message}") }
            .getOrNull()?.also { cached = it } ?: return false
        return AudioEngine.previewSamples(clip.first, clip.second)
    }

    /** 16-bit PCM WAV to interleaved floats and the channel count. */
    private fun decodeWav(bytes: ByteArray): Pair<FloatArray, Int> {
        fun int16(at: Int) = (bytes[at].toInt() and 0xFF) or (bytes[at + 1].toInt() shl 8)
        fun int32(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or (bytes[at + 3].toInt() shl 24)
        fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE") { "not a WAV file" }
        var channels = 0
        var position = 12
        while (position + 8 <= bytes.size) {
            val size = int32(position + 4)
            val body = position + 8
            when (tag(position)) {
                "fmt " -> {
                    channels = int16(body + 2)
                    require(int16(body + 14) == 16) { "only 16-bit PCM" }
                }
                "data" -> {
                    require(channels in 1..2) { "unsupported channel count" }
                    val count = minOf(size, bytes.size - body) / 2
                    return FloatArray(count) { int16(body + it * 2).toShort() / 32768f } to channels
                }
            }
            position = body + size + (size and 1)
        }
        error("no audio data")
    }
}

/** An audition that stops when the screen or dialog using it goes away. */
@Composable
fun rememberEffectAudition(source: AuditionSource): EffectAudition {
    val scope = rememberCoroutineScope()
    val audition = remember(source) { EffectAudition(scope, source) }
    DisposableEffect(audition) { onDispose { audition.stop() } }
    return audition
}

/**
 * ▶ Listen (a clip in a loop) and, for track chains, Live (the microphone) side by side. While either runs, the
 * A/B switch "with | without" this effect appears below, followed by at most one short note.
 */
@Composable
fun AuditionControls(audition: EffectAudition, key: Any, with: () -> List<EffectSlot>, without: () -> List<EffectSlot>,
                     live: Boolean, modifier: Modifier = Modifier) {
    val playing = audition.playing == key
    val liveActive = audition.live == key
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { audition.toggle(key, with(), without()) }) {
                Icon(painterResource(if (playing) Res.drawable.ic_stop else Res.drawable.ic_play), contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Text(stringResource(if (playing) Res.string.effect_audition_stop else Res.string.effect_audition_play),
                    modifier = Modifier.padding(start = 8.dp))
            }
            if (live && LiveInputs.current != null) {
                FilledTonalButton(onClick = { audition.toggleLive(key, with(), without()) }) {
                    Icon(painterResource(if (liveActive) Res.drawable.ic_stop else Res.drawable.ic_mic), contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Text(stringResource(if (liveActive) Res.string.live_stop else Res.string.live_start),
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (playing || liveActive) {
            Box(Modifier.padding(top = 8.dp)) { AuditionABSwitch(audition.bypassed, audition::setBypass) }
        }
        val message = audition.liveMessage
        val note = when {
            message != null -> message
            liveActive -> Res.string.live_hint
            playing -> audition.sourceKind?.let {
                when (it) {
                    AuditionSourceKind.Track -> Res.string.effect_audition_source_track
                    AuditionSourceKind.Mix -> Res.string.effect_audition_source_mix
                    AuditionSourceKind.Sample -> Res.string.effect_audition_source_sample
                }
            }
            else -> null
        }
        note?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    audition.pendingWarning?.let { warning ->
        LiveWarningDialog(warning, onContinue = audition::confirmLiveWarning, onCancel = audition::dismissLiveWarning)
    }
}

/** Compact "with | without" choice, styled like the input channel switch of the project view. */
@Composable
internal fun AuditionABSwitch(bypassed: Boolean, onBypass: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.height(32.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).selectableGroup()) {
        listOf(false to Res.string.effect_audition_with, true to Res.string.effect_audition_without).forEach { (value, label) ->
            val checked = value == bypassed
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(68.dp)
                    .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(checked, role = Role.RadioButton) { onBypass(value) }
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Before monitoring on the loudspeaker (feedback) or Bluetooth (delay); the user may go on. */
@Composable
fun LiveWarningDialog(warning: LiveWarning, onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.live_warning_title)) },
        text = {
            Text(stringResource(when (warning) {
                LiveWarning.Speaker -> Res.string.live_warning_speaker
                LiveWarning.Bluetooth -> Res.string.live_warning_bluetooth
            }))
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(Res.string.live_warning_continue)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) } }
    )
}
