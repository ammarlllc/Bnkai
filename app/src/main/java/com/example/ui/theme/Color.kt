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

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

enum class ChatBackgroundTheme(
    val id: String,
    val label: String,
    val bgTop: Color,
    val bgBottom: Color,
    val patternColor: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val accent: Color
) {
    OBSIDIAN_CYBER(
        id = "obsidian_cyber",
        label = "Obsidian Cyber",
        bgTop = Color(0xFF070B14),
        bgBottom = Color(0xFF0D1729),
        patternColor = Color(0xFF00E5FF),
        outgoingBubble = Color(0xFF134E6F),
        incomingBubble = Color(0xFF151F33),
        accent = Color(0xFF00E5FF)
    ),
    MIDNIGHT_AURORA(
        id = "midnight_aurora",
        label = "Midnight Aurora",
        bgTop = Color(0xFF09081A),
        bgBottom = Color(0xFF151238),
        patternColor = Color(0xFF38BDF8),
        outgoingBubble = Color(0xFF2B3A8C),
        incomingBubble = Color(0xFF181936),
        accent = Color(0xFF60A5FA)
    ),
    EMERALD_MATRIX(
        id = "emerald_matrix",
        label = "Emerald Matrix",
        bgTop = Color(0xFF04120E),
        bgBottom = Color(0xFF08241C),
        patternColor = Color(0xFF10B981),
        outgoingBubble = Color(0xFF0E5A44),
        incomingBubble = Color(0xFF102A22),
        accent = Color(0xFF34D399)
    ),
    CRIMSON_STEALTH(
        id = "crimson_stealth",
        label = "Crimson Stealth",
        bgTop = Color(0xFF12070B),
        bgBottom = Color(0xFF220C14),
        patternColor = Color(0xFFF43F5E),
        outgoingBubble = Color(0xFF6D1D33),
        incomingBubble = Color(0xFF26121A),
        accent = Color(0xFFFB7185)
    ),
    DEEP_OCEAN(
        id = "deep_ocean",
        label = "Deep Ocean",
        bgTop = Color(0xFF04111D),
        bgBottom = Color(0xFF0A243B),
        patternColor = Color(0xFF22D3EE),
        outgoingBubble = Color(0xFF0E5377),
        incomingBubble = Color(0xFF10283E),
        accent = Color(0xFF22D3EE)
    );

    companion object {
        fun fromId(id: String?): ChatBackgroundTheme =
            entries.firstOrNull { it.id == id } ?: OBSIDIAN_CYBER
    }
}
