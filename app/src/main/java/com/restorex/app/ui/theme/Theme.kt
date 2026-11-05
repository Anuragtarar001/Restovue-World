package com.restorex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RestoreXBackground = Color(0xFF101114)
val RestoreXPanel = Color(0xFF1C1D20)
val RestoreXPanelRaised = Color(0xFF25262A)
val RestoreXAccent = Color(0xFFFFB323)
val RestoreXText = Color(0xFFF7F7F8)
val RestoreXMuted = Color(0xFF9A9CA2)
val RestoreXDisabled = Color(0xFF5D5F65)

private val RestoreXColors = darkColorScheme(
    primary = RestoreXAccent,
    onPrimary = Color(0xFF251A00),
    secondary = RestoreXAccent,
    background = RestoreXBackground,
    onBackground = RestoreXText,
    surface = RestoreXPanel,
    onSurface = RestoreXText,
    surfaceVariant = RestoreXPanelRaised,
    onSurfaceVariant = RestoreXMuted,
    outline = Color(0xFF3A3B40),
    error = Color(0xFFFF6B6B),
)

@Composable
fun RestoreXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RestoreXColors,
        typography = Typography(),
        content = content,
    )
}
