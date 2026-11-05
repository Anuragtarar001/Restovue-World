package com.restorex.app.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    @Test
    fun undoAndRedoWalkCommittedStates() {
        val history = EditHistory("original")
        history.commit("bright")
        history.commit("rotated")

        assertEquals("bright", history.undo())
        assertTrue(history.canRedo)
        assertEquals("original", history.undo())
        assertFalse(history.canUndo)
        assertEquals("bright", history.redo())
        assertEquals("rotated", history.redo())
    }

    @Test
    fun newCommitClearsRedoBranch() {
        val history = EditHistory(0)
        history.commit(1)
        history.commit(2)
        history.undo()

        history.commit(3)

        assertFalse(history.canRedo)
        assertEquals(3, history.current)
    }

    @Test
    fun duplicateCommitDoesNotCreateHistoryEntry() {
        val history = EditHistory(1)
        history.commit(1)
        assertFalse(history.canUndo)
    }
}
