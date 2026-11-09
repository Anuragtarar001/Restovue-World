package com.restorex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Restovue production palette from the public brand system. Keep these semantic names so every
// existing editor surface inherits the approved identity without duplicating raw colour values.
val RestoreXBackground = Color(0xFF06091F)
val RestoreXPanel = Color(0xFF0F1235)
val RestoreXPanelRaised = Color(0xFF18123B)
val RestoreXAccent = Color(0xFFE62CA2)
val RestoreXAccentViolet = Color(0xFF8B32E8)
val RestoreXCyan = Color(0xFF20D6FE)
val RestoreXText = Color(0xFFF8F4FD)
val RestoreXMuted = Color(0xFFA19BB5)
val RestoreXDisabled = Color(0xFF68627C)

private val RestoreXColors = darkColorScheme(
    primary = RestoreXAccent,
    onPrimary = Color.White,
    secondary = RestoreXAccent,
    background = RestoreXBackground,
    onBackground = RestoreXText,
    surface = RestoreXPanel,
    onSurface = RestoreXText,
    surfaceVariant = RestoreXPanelRaised,
    onSurfaceVariant = RestoreXMuted,
    outline = Color(0xFF302A59),
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
