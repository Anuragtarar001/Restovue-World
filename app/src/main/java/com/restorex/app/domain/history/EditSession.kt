package com.restorex.app.domain.history

import com.restorex.app.domain.model.EditState

/** Separates continuously changing preview state from discrete undoable editor actions. */
class EditSession(initial: EditState = EditState()) {
    private val history = EditHistory(initial)

    var draft: EditState = initial
        private set

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun updateDraft(transform: (EditState) -> EditState): EditState {
        draft = transform(draft)
        return draft
    }

    fun commitDraft(): EditState {
        draft = history.commit(draft)
        return draft
    }

    fun commit(value: EditState): EditState {
        draft = history.commit(value)
        return draft
    }

    fun undo(): EditState {
        draft = history.undo()
        return draft
    }

    fun redo(): EditState {
        draft = history.redo()
        return draft
    }
}
