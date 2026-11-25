package com.aibuilt.arin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Color_primaryContainerLight = Color(0xFFD3E7FF)
private val Color_primaryContainerDark = Color(0xFF10324F)

private val Light = lightColorScheme(
    primary = Sky,
    onPrimary = Paper,
    primaryContainer = Color_primaryContainerLight,
    onPrimaryContainer = Ink,
    secondary = Mint,
    onSecondary = Paper,
    tertiary = Amber,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = Ink,
    outline = OutlineLight,
    error = ErrLight
)

private val Dark = darkColorScheme(
    primary = SkyDark,
    onPrimary = Ink,
    primaryContainer = Color_primaryContainerDark,
    onPrimaryContainer = SkyDark,
    secondary = MintLight,
    onSecondary = Ink,
    tertiary = AmberLight,
    onTertiary = Ink,
    background = PaperDark,
    onBackground = Paper,
    surface = PaperDark,
    onSurface = Paper,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = Paper,
    outline = OutlineDark,
    error = ErrDark
)

@Composable
fun ArinTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = ArinTypography,
        content = content
    )
}
