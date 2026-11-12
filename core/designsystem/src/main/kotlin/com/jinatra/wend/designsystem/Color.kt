package com.jinatra.wend.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Paper and ink. One crimson accent, reserved for "now" and the primary action.
internal object Paper {
    val ground = Color(0xFFF7F3EC)
    val raised = Color(0xFFFBF8F3)
    val ink = Color(0xFF1B1714)
    val inkSecondary = Color(0xFF5F5750)
    val rule = Color(0xFFDCD3C7)
    val track = Color(0xFFEAE3D9)
    val crimson = Color(0xFFA3122C)
    val crimsonContainer = Color(0xFFF3DEDD)
    val onCrimsonContainer = Color(0xFF5C0A19)
}

internal object Night {
    val ground = Color(0xFF16120F)
    val raised = Color(0xFF211C18)
    val ink = Color(0xFFF1EBE3)
    val inkSecondary = Color(0xFFB5ABA0)
    val rule = Color(0xFF3B332D)
    val track = Color(0xFF2B2420)
    val crimson = Color(0xFFE5485F)
    val onCrimson = Color(0xFF1A0408)
    val crimsonContainer = Color(0xFF5C1522)
    val onCrimsonContainer = Color(0xFFFFD9DC)
}

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Paper.crimson,
    onPrimary = Paper.raised,
    primaryContainer = Paper.crimsonContainer,
    onPrimaryContainer = Paper.onCrimsonContainer,
    secondary = Paper.ink,
    onSecondary = Paper.ground,
    secondaryContainer = Paper.track,
    onSecondaryContainer = Paper.ink,
    tertiary = Paper.inkSecondary,
    onTertiary = Paper.ground,
    background = Paper.ground,
    onBackground = Paper.ink,
    surface = Paper.ground,
    onSurface = Paper.ink,
    surfaceVariant = Paper.track,
    onSurfaceVariant = Paper.inkSecondary,
    surfaceContainerLowest = Paper.raised,
    surfaceContainerLow = Paper.raised,
    surfaceContainer = Paper.raised,
    surfaceContainerHigh = Paper.raised,
    surfaceContainerHighest = Paper.track,
    inverseSurface = Paper.ink,
    inverseOnSurface = Paper.ground,
    inversePrimary = Night.crimson,
    outline = Paper.inkSecondary,
    outlineVariant = Paper.rule,
    error = Paper.crimson,
    onError = Paper.raised,
    scrim = Color(0x99000000),
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Night.crimson,
    onPrimary = Night.onCrimson,
    primaryContainer = Night.crimsonContainer,
    onPrimaryContainer = Night.onCrimsonContainer,
    secondary = Night.ink,
    onSecondary = Night.ground,
    secondaryContainer = Night.track,
    onSecondaryContainer = Night.ink,
    tertiary = Night.inkSecondary,
    onTertiary = Night.ground,
    background = Night.ground,
    onBackground = Night.ink,
    surface = Night.ground,
    onSurface = Night.ink,
    surfaceVariant = Night.track,
    onSurfaceVariant = Night.inkSecondary,
    surfaceContainerLowest = Night.ground,
    surfaceContainerLow = Night.raised,
    surfaceContainer = Night.raised,
    surfaceContainerHigh = Night.raised,
    surfaceContainerHighest = Night.track,
    inverseSurface = Night.ink,
    inverseOnSurface = Night.ground,
    inversePrimary = Paper.crimson,
    outline = Night.inkSecondary,
    outlineVariant = Night.rule,
    error = Night.crimson,
    onError = Night.onCrimson,
    scrim = Color(0xCC000000),
)
