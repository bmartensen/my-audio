package com.martensening.my_audio.data

import kotlinx.serialization.json.JsonObject

/**
 * Fields of one `project.json` object that this app version does not know, typically written by a newer
 * version. They are kept as read and written back on save, so passing a project between app versions loses
 * nothing. [own] holds the unknown keys of the object itself; [nested] holds the unknown keys inside its
 * known sub-objects (for example `metronome` or `loop`), by the sub-object's key.
 */
data class UnknownFields(
    val own: JsonObject = EMPTY_OBJECT,
    val nested: Map<String, JsonObject> = emptyMap()
) {
    val isEmpty get() = own.isEmpty() && nested.isEmpty()

    companion object {
        private val EMPTY_OBJECT = JsonObject(emptyMap())
        val NONE = UnknownFields()
    }
}
