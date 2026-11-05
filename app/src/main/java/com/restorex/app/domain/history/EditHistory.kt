package com.restorex.app.domain.history

class EditHistory<T>(initial: T) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(value: T): T {
        if (value == current) return current
        undoStack.addLast(current)
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
}
