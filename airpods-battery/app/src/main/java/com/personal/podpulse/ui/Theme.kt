package com.personal.podpulse.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg0 = Color(0xFF0B0F1A)
val Bg1 = Color(0xFF141B2E)
val Mint = Color(0xFF34E89E)
val Amber = Color(0xFFFFC857)
val Coral = Color(0xFFFF5E6C)
val Sky = Color(0xFF5AC8FA)
val Muted = Color(0xFF8A93A8)
val TextHi = Color(0xFFF4F6FB)
val Glass = Color(0x14FFFFFF)
val GlassBorder = Color(0x1FFFFFFF)
val RingTrack = Color(0x1AFFFFFF)

fun levelColor(level: Int?): Color = when {
    level == null -> Muted
    level >= 50 -> Mint
    level >= 20 -> Amber
    else -> Coral
}

private val Scheme = darkColorScheme(
    primary = Mint,
    onPrimary = Bg0,
    secondary = Sky,
    background = Bg0,
    onBackground = TextHi,
    surface = Bg1,
    onSurface = TextHi,
    onSurfaceVariant = Muted,
)

@Composable
fun PodPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
