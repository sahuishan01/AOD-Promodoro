package com.algosculptor.pomodoro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AOD-safe dark palette: low-luminance surfaces, tinted (never pure-white) accents.
 * The timer screen overrides surface/accent per selected background; this scheme
 * covers settings and system surfaces.
 */
private val AodDarkScheme = darkColorScheme(
    primary = Color(0xFFF5F1E3),
    onPrimary = Color(0xFF0B0E14),
    secondary = Color(0xFFE4572E),
    onSecondary = Color(0xFF0B0E14),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFF5F1E3),
    surface = Color(0xFF141923),
    onSurface = Color(0xFFF5F1E3),
    surfaceVariant = Color(0xFF1F2636),
    onSurfaceVariant = Color(0xFFD4CEC1),
    outline = Color(0xFF4A5568),
)

@Composable
fun PomodoroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AodDarkScheme,
        typography = PomodoroTypography,
        content = content,
    )
}
