package com.maverick.mavemap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF062033),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF062522),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF10151B),
    onBackground = Color(0xFFE8F0F5),
    surface = Color(0xFF1A2028),
    onSurface = Color(0xFFE8F0F5),
    surfaceVariant = Color(0xFF2B333D),
    onSurfaceVariant = Color(0xFFC1CBD3)
)

@Composable
fun MAVEMapTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}