package com.restorex.app.domain.history

class EditHistory<T>(initial: T, private val maxUndoEntries: Int = DEFAULT_MAX_UNDO_ENTRIES) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    init { require(maxUndoEntries > 0) }

    var current: T = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size
    var discardedUndoCount: Int = 0
        private set

    fun commit(value: T): T {
        if (value == current) return current
        undoStack.addLast(current)
        if (undoStack.size > maxUndoEntries) {
            undoStack.removeFirst()
            discardedUndoCount++
        }
        current = value
        redoStack.clear()
        return current
    }

    fun replaceCurrent(value: T): T {
        current = value
        return current
    }

    fun undo(): T {
        if (undoStack.isEmpty()) return current
        redoStack.addLast(current)
        current = undoStack.removeLast()
        return current
    }

    fun redo(): T {
        if (redoStack.isEmpty()) return current
        undoStack.addLast(current)
        current = redoStack.removeLast()
        return current
    }

    private companion object { const val DEFAULT_MAX_UNDO_ENTRIES = 100 }
}
