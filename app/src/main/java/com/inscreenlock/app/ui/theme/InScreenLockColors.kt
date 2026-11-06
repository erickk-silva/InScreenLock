package com.inscreenlock.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Named design tokens from the InScreenLock visual system. */
object InScreenLockColors {
    val Background = Color(0xFF12161C)
    val Surface = Color(0xFF1F232B)
    val SurfaceVariant = Color(0xFF292F39)
    val Primary = Color(0xFF22E3B0)
    val PrimaryDark = Color(0xFF16C79A)
    val Alert = Color(0xFFFF6B7A)
    val Highlight = Color(0xFF8B5CF6)
    val TextPrimary = Color(0xFFE6EAF0)
    val TextSecondary = Color(0xFF9AA4B2)
    val Outline = Color(0xFF515C6A)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
}

val InScreenLockDarkColorScheme = darkColorScheme(
    primary = InScreenLockColors.Primary,
    onPrimary = InScreenLockColors.Background,
    primaryContainer = InScreenLockColors.PrimaryDark,
    onPrimaryContainer = InScreenLockColors.Background,
    secondary = InScreenLockColors.Highlight,
    onSecondary = InScreenLockColors.White,
    tertiary = InScreenLockColors.Alert,
    onTertiary = InScreenLockColors.Background,
    background = InScreenLockColors.Background,
    onBackground = InScreenLockColors.TextPrimary,
    surface = InScreenLockColors.Surface,
    onSurface = InScreenLockColors.TextPrimary,
    surfaceVariant = InScreenLockColors.SurfaceVariant,
    onSurfaceVariant = InScreenLockColors.TextSecondary,
    error = InScreenLockColors.Alert,
    onError = InScreenLockColors.Background,
    outline = InScreenLockColors.Outline,
)
