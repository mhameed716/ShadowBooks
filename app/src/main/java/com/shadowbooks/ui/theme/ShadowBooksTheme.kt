package com.shadowbooks.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShadowBooksColors = darkColorScheme(
    primary = Color(0xFF65D4FA),
    onPrimary = Color(0xFF071522),
    secondary = Color(0xFFFFC86B),
    onSecondary = Color(0xFF2B1900),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFF3F7FC),
    surface = Color(0xFF111A2A),
    onSurface = Color(0xFFF3F7FC),
    surfaceVariant = Color(0xFF202B3E),
    onSurfaceVariant = Color(0xFFB9C6D8),
)

@Composable
fun ShadowBooksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShadowBooksColors,
        content = content,
    )
}
