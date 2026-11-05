package com.restorex.app.domain.history

import com.restorex.app.domain.model.EditState
import com.restorex.app.domain.model.FilterSelection
import com.restorex.app.domain.model.Adjustments
import com.restorex.app.domain.model.BrushEditType
import com.restorex.app.domain.model.BrushStroke
import com.restorex.app.domain.model.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionTest {
    @Test
    fun localOperationsUndoAsCompactStateDescriptions() {
        val session = EditSession()
        val operation = BrushStroke(1, BrushEditType.HEAL, listOf(NormalizedPoint(.5f, .5f)))
        session.commit(EditState(localEdits = listOf(operation)))

        assertEquals(1, session.draft.localEdits.size)
        assertTrue(session.undo().localEdits.isEmpty())
        assertEquals(operation, session.redo().localEdits.single())
    }

    @Test
    fun sliderDraftsBecomeOneHistoryEntryWhenCommitted() {
        val session = EditSession()

        repeat(100) { step ->
            session.updateDraft { edits ->
                edits.copy(adjustments = edits.adjustments.copy(brightness = step / 100f))
            }
        }

        assertFalse(session.canUndo)
        session.commitDraft()
        assertTrue(session.canUndo)
        assertEquals(0f, session.undo().adjustments.brightness)
        assertFalse(session.canUndo)
    }

    @Test
    fun resetIsUndoableAndNewEditClearsRedo() {
        val session = EditSession()
        val bright = EditState(adjustments = EditState().adjustments.copy(brightness = 0.4f))
        session.commit(bright)
        session.commit(EditState())

        assertEquals(bright, session.undo())
        session.commit(EditState(adjustments = EditState().adjustments.copy(exposure = 1f)))

        assertFalse(session.canRedo)
    }

    @Test
    fun filterSelectionAndResetParticipateInUndoRedo() {
        val session = EditSession()
        val filtered = EditState(filter = FilterSelection("cinema", .6f, Adjustments(contrast = 1.2f)))
        session.commit(filtered)
        session.commit(EditState())

        assertEquals(filtered, session.undo())
        assertEquals(EditState(), session.redo())
    }
}
