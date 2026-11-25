package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Obsidian & Electric Cyan Palette
val ObsidianBg = Color(0xFF070B14)
val ObsidianSurface = Color(0xFF0E1525)
val ObsidianSurfaceHigh = Color(0xFF162036)
val ObsidianOutline = Color(0xFF23324D)

val ElectricCyan = Color(0xFF00E5FF)
val CobaltAccent = Color(0xFF3B82F6)
val EmeraldSignal = Color(0xFF10B981)
val AmberWarn = Color(0xFFF59E0B)
val CrimsonAlert = Color(0xFFEF4444)
val VerifiedBlue = Color(0xFF2AABEE)
val VerifiedGreen = Color(0xFF22C55E)
val VerifiedRed = Color(0xFFEF4444)

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

enum class ChatBackgroundTheme(
    val id: String,
    val label: String,
    val bgTop: Color,
    val bgBottom: Color,
    val patternColor: Color,
    val secondaryGlow: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val accent: Color
) {
    SPIDY(
        id = "spidy",
        label = "Spidy 3D",
        bgTop = Color(0xFF140711),
        bgBottom = Color(0xFF0B1430),
        patternColor = Color(0xFFFF2E63),
        secondaryGlow = Color(0xFF3B82F6),
        outgoingBubble = Color(0xFF7B1638),
        incomingBubble = Color(0xFF172042),
        accent = Color(0xFFFF3B6B)
    ),
    GOJO(
        id = "gojo",
        label = "Gojo Void 3D",
        bgTop = Color(0xFF07091D),
        bgBottom = Color(0xFF170B3B),
        patternColor = Color(0xFF38BDF8),
        secondaryGlow = Color(0xFFA855F7),
        outgoingBubble = Color(0xFF2C2773),
        incomingBubble = Color(0xFF14183B),
        accent = Color(0xFF60A5FA)
    ),
    SUKUNA(
        id = "sukuna",
        label = "Sukuna Shrine 3D",
        bgTop = Color(0xFF160408),
        bgBottom = Color(0xFF2B070E),
        patternColor = Color(0xFFEF4444),
        secondaryGlow = Color(0xFFF97316),
        outgoingBubble = Color(0xFF7A1220),
        incomingBubble = Color(0xFF291016),
        accent = Color(0xFFFF4D5A)
    ),
    HACKER(
        id = "hacker",
        label = "Hacker 3D",
        bgTop = Color(0xFF03100B),
        bgBottom = Color(0xFF061E16),
        patternColor = Color(0xFF00FF9C),
        secondaryGlow = Color(0xFF00E5FF),
        outgoingBubble = Color(0xFF0D5940),
        incomingBubble = Color(0xFF0D241C),
        accent = Color(0xFF00FF9C)
    ),
    MAKIMA(
        id = "makima",
        label = "Makima 3D",
        bgTop = Color(0xFF17080C),
        bgBottom = Color(0xFF241118),
        patternColor = Color(0xFFF59E0B),
        secondaryGlow = Color(0xFFE11D48),
        outgoingBubble = Color(0xFF6E2133),
        incomingBubble = Color(0xFF26151C),
        accent = Color(0xFFFBBF24)
    ),
    YUTA(
        id = "yuta",
        label = "Yuta Rika 3D",
        bgTop = Color(0xFF08091C),
        bgBottom = Color(0xFF1B1033),
        patternColor = Color(0xFF22D3EE),
        secondaryGlow = Color(0xFFF472B6),
        outgoingBubble = Color(0xFF2D2E75),
        incomingBubble = Color(0xFF181938),
        accent = Color(0xFF22D3EE)
    ),
    CUSTOM_GALLERY(
        id = "custom_gallery",
        label = "Gallery Theme",
        bgTop = Color(0xFF070B14),
        bgBottom = Color(0xFF0D1729),
        patternColor = Color(0xFF00E5FF),
        secondaryGlow = Color(0xFF3B82F6),
        outgoingBubble = Color(0xFF134E6F),
        incomingBubble = Color(0xFF151F33),
        accent = Color(0xFF00E5FF)
    );

    companion object {
        val animatedPresets: List<ChatBackgroundTheme> =
            listOf(SPIDY, GOJO, SUKUNA, HACKER, MAKIMA, YUTA)

        fun fromId(id: String?): ChatBackgroundTheme =
            entries.firstOrNull { it.id == id } ?: HACKER
    }
}
