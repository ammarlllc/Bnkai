package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val AmmarDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = Color(0xFF0D3A4D),
    onPrimaryContainer = Color(0xFFB5F5FF),
    secondary = CobaltAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172C57),
    onSecondaryContainer = Color(0xFFD8E6FF),
    tertiary = EmeraldSignal,
    onTertiary = Color(0xFF002116),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianOutline,
    error = CrimsonAlert,
    onError = Color.White
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun AmmarChatTheme(
    accentColor: Color = ElectricCyan,
    content: @Composable () -> Unit
) {
    val scheme = AmmarDarkColorScheme.copy(
        primary = accentColor,
        secondary = CobaltAccent
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
