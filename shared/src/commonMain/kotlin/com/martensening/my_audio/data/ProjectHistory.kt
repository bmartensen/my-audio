package com.martensening.my_audio.data

/**
 * Undo and redo of one open project: complete project states in memory, never saved. Before each change the
 * session records the state it leaves; undo returns to it and keeps the current state for redo. Changes of the
 * same [record] key in a row (e.g. the steps of one slider) form one step.
 */
class ProjectHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()
    private var lastKey: String? = null

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /**
     * Records [before], the state a change leaves. With the same non-null [key] as the previous change, the
     * earlier state stays the step's start instead. A new change clears the redo states.
     */
    fun record(before: Project, key: String? = null) {
        redo.clear()
        if (key != null && key == lastKey && undo.isNotEmpty()) return
        lastKey = key
        if (undo.lastOrNull() == before) return
        undo.addLast(before)
        while (undo.size > limit) undo.removeFirst()
    }

    /** The state to return to from [current], or null when there is none; [current] becomes redoable. */
    fun undo(current: Project): Project? {
        val target = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastKey = null
        return target
    }

    /** The state undone last, or null; [current] becomes undoable again. */
    fun redo(current: Project): Project? {
        val target = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastKey = null
        return target
    }

    fun clear() {
        undo.clear()
        redo.clear()
        lastKey = null
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
