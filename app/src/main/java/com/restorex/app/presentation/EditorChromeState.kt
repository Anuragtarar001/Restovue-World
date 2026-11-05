package com.restorex.app.presentation

/** Pure layout policy used by Compose and unit tests; essential actions never follow immersive visibility. */
data class EditorChromeState(val isFullScreen: Boolean = false) {
    val showTopToolbar: Boolean = true
    val showExport: Boolean = true
    val showBottomTools: Boolean = !isFullScreen
    val showExitFullScreen: Boolean = isFullScreen
}
