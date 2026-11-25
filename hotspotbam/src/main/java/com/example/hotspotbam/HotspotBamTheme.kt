package com.example.hotspotbam

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ObsidianBg = Color(0xFF070B14)
val ObsidianSurface = Color(0xFF0E1525)
val ObsidianSurfaceHigh = Color(0xFF162036)
val ObsidianOutline = Color(0xFF23324D)
val ElectricCyan = Color(0xFF00E5FF)
val EmeraldSignal = Color(0xFF10B981)
val AmberWarn = Color(0xFFF59E0B)
val CrimsonAlert = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private val BamTypography = Typography(
    headlineMedium = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun HotspotBamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EmeraldSignal,
            background = ObsidianBg,
            surface = ObsidianSurface,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        typography = BamTypography,
        content = content
    )
}
