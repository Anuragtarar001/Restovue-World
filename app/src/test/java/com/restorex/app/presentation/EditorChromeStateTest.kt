package com.restorex.app.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorChromeStateTest {
    @Test
    fun fullScreenKeepsEssentialToolbarAndExportVisible() {
        val chrome = EditorChromeState(isFullScreen = true)

        assertTrue(chrome.showTopToolbar)
        assertTrue(chrome.showExport)
        assertTrue(chrome.showExitFullScreen)
        assertFalse(chrome.showBottomTools)
    }

    @Test
    fun leavingFullScreenRestoresEditingTools() {
        val chrome = EditorChromeState(isFullScreen = false)

        assertTrue(chrome.showTopToolbar)
        assertTrue(chrome.showExport)
        assertTrue(chrome.showBottomTools)
        assertFalse(chrome.showExitFullScreen)
    }
}
