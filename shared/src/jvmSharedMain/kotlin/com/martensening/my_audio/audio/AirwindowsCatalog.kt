package com.martensening.my_audio.audio

/** One Airwindows effect of the catalog built into the native engine. */
data class AirwindowsEffect(
    /** Position in the native catalog; only valid within this build. */
    val index: Int,
    val name: String,
    /** Airwindows' own category, e.g. "Reverb" or "Tape". */
    val category: String,
    /** The original English one-line description. */
    val description: String
)

/** The Airwindows effects of the native engine (`third_party/airwindows`), sorted by name. */
object AirwindowsCatalog {
    val effects: List<AirwindowsEffect> by lazy {
        NativeAudio.airwindowsCatalog().toList().chunked(3).mapIndexed { index, (name, category, description) ->
            AirwindowsEffect(index, name, category, description)
        }
    }

    private val byName by lazy { effects.associateBy { it.name } }
    private val names = mutableMapOf<String, List<String>>()
    private val defaults = mutableMapOf<String, List<Float>>()

    /** The effect, or null when this build does not know the name (e.g. a project from a newer app). */
    fun find(name: String): AirwindowsEffect? = byName[name]

    @Synchronized
    fun parameterNames(name: String): List<String> = find(name)?.let { effect ->
        names.getOrPut(name) { NativeAudio.airwindowsParameterNames(effect.index).toList() }
    }.orEmpty()

    @Synchronized
    fun defaults(name: String): List<Float> = find(name)?.let { effect ->
        defaults.getOrPut(name) { NativeAudio.airwindowsDefaults(effect.index).toList() }
    }.orEmpty()

    /** [values] completed with the defaults to exactly the effect's parameter count. */
    fun complete(name: String, values: List<Float>): List<Float> {
        val base = defaults(name)
        return base.indices.map { values.getOrNull(it)?.coerceIn(0f, 1f) ?: base[it] }
    }

    /** Value and unit as the effect itself shows them, e.g. "-3.00 dB"; numbers keep two decimals. */
    fun display(name: String, values: List<Float>): List<String> = find(name)?.let { effect ->
        NativeAudio.airwindowsDisplay(effect.index, complete(name, values).toFloatArray()).map(::shorten)
    }.orEmpty()

    /** Airwindows prints numbers with four decimals ("0.5000"); two read better and suffice. */
    internal fun shorten(text: String): String = DECIMALS.replace(text) { it.groupValues[1] }

    private val DECIMALS = Regex("""^(-?\d+\.\d{2})\d+""")
}
