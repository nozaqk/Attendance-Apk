package com.smartattendance.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Web palette (src/shared/styles.css) — reused so the native app matches the web design.
val Navy = Color(0xFF12263F)
val Navy2 = Color(0xFF1D3A5F)
val AppBg = Color(0xFFF3F5F8)
val CardWhite = Color(0xFFFFFFFF)
val TextDark = Color(0xFF1B2430)
val Muted = Color(0xFF5D6B7C)
val Line = Color(0xFFDDE3EA)
val Green = Color(0xFF1A7F4B)
val GreenBg = Color(0xFFE3F4EA)
val Yellow = Color(0xFF8A5A00)
val YellowBg = Color(0xFFFFF2CC)
val Red = Color(0xFFB42318)
val RedBg = Color(0xFFFDE7E4)
val Blue = Color(0xFF1D4ED8)
val BlueBg = Color(0xFFE5EDFF)
val GreyBg = Color(0xFFE9EDF2)

private val Scheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Navy2,
    onSecondary = Color.White,
    // Web look: white fields with a --line border, navy focus ring (primary).
    outline = Line,
    outlineVariant = Line,
    surfaceVariant = CardWhite,
    background = AppBg,
    surface = CardWhite,
    onBackground = TextDark,
    onSurface = TextDark,
    error = Red
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
