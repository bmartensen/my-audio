package com.martensening.my_audio.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * The user's effect selection from the library on the home screen: only selected effects are offered
 * by "Add effect". A device setting in `library.json` next to the projects, not part of any project;
 * effects already used in a project keep working when they are deselected.
 */
class EffectLibrary(private val file: File) {
    private val _selection = MutableStateFlow(load())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private fun load(): Set<String> = try {
        val json = Json.parseToJsonElement(file.readText()) as JsonObject
        (json["selected"] as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
    } catch (_: Exception) {
        // No or unreadable file: the easy My Audio effects are selected, nothing else.
        DEFAULT
    }

    @Synchronized
    fun setSelected(id: String, selected: Boolean) {
        val next = if (selected) _selection.value + id else _selection.value - id
        if (next == _selection.value) return
        _selection.value = next
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(buildJsonObject {
            put("selected", JsonArray(next.sorted().map(::JsonPrimitive)))
        }.toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    companion object {
        const val TONE = "myaudio.eq"
        const val REVERB = "myaudio.reverb"
        private const val AIRWINDOWS = "airwindows."
        val DEFAULT = setOf(TONE, REVERB)

        fun airwindowsId(name: String) = AIRWINDOWS + name
        fun airwindowsName(id: String): String? = id.takeIf { it.startsWith(AIRWINDOWS) }?.removePrefix(AIRWINDOWS)

        private val instances = mutableMapOf<String, EffectLibrary>()

        /** The app-wide selection stored below [root], next to the projects. */
        @Synchronized
        fun forRoot(root: File): EffectLibrary =
            instances.getOrPut(root.absolutePath) { EffectLibrary(File(root, "library.json")) }
    }
}
