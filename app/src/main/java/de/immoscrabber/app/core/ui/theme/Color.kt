package de.immoscrabber.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Festes Material-3-Farbschema aus dem Favicon-Blau #1B3A6B als Seed (Entscheidung #20).
 *
 * Erzeugt mit Googles @material/material-color-utilities 0.4.0 (derselben Bibliothek, die der
 * Material Theme Builder nutzt): SchemeTonalSpot (Standard des Theme Builders, "Color match" aus),
 * Kontrast 0, je einmal hell und dunkel; alle Rollen über MaterialDynamicColors ausgelesen.
 * Neu erzeugen: siehe docs/theme.md.
 */

private val primaryLight = Color(0xFF425E91)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFD7E2FF)
private val onPrimaryContainerLight = Color(0xFF294677)
private val inversePrimaryLight = Color(0xFFACC7FF)
private val secondaryLight = Color(0xFF565E71)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFDAE2F9)
private val onSecondaryContainerLight = Color(0xFF3F4759)
private val tertiaryLight = Color(0xFF705574)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFFBD7FC)
private val onTertiaryContainerLight = Color(0xFF573E5B)
private val backgroundLight = Color(0xFFF9F9FF)
private val onBackgroundLight = Color(0xFF1A1C20)
private val surfaceLight = Color(0xFFF9F9FF)
private val onSurfaceLight = Color(0xFF1A1C20)
private val surfaceVariantLight = Color(0xFFE0E2EC)
private val onSurfaceVariantLight = Color(0xFF44474E)
private val surfaceTintLight = Color(0xFF425E91)
private val inverseSurfaceLight = Color(0xFF2E3036)
private val inverseOnSurfaceLight = Color(0xFFF0F0F7)
private val errorLight = Color(0xFFBA1A1A)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF93000A)
private val outlineLight = Color(0xFF74777F)
private val outlineVariantLight = Color(0xFFC4C6D0)
private val scrimLight = Color(0xFF000000)
private val surfaceBrightLight = Color(0xFFF9F9FF)
private val surfaceContainerLight = Color(0xFFEDEDF4)
private val surfaceContainerHighLight = Color(0xFFE8E7EE)
private val surfaceContainerHighestLight = Color(0xFFE2E2E9)
private val surfaceContainerLowLight = Color(0xFFF3F3FA)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceDimLight = Color(0xFFD9D9E0)

private val primaryDark = Color(0xFFACC7FF)
private val onPrimaryDark = Color(0xFF0E2F60)
private val primaryContainerDark = Color(0xFF294677)
private val onPrimaryContainerDark = Color(0xFFD7E2FF)
private val inversePrimaryDark = Color(0xFF425E91)
private val secondaryDark = Color(0xFFBEC6DC)
private val onSecondaryDark = Color(0xFF283041)
private val secondaryContainerDark = Color(0xFF3F4759)
private val onSecondaryContainerDark = Color(0xFFDAE2F9)
private val tertiaryDark = Color(0xFFDDBCE0)
private val onTertiaryDark = Color(0xFF3F2844)
private val tertiaryContainerDark = Color(0xFF573E5B)
private val onTertiaryContainerDark = Color(0xFFFBD7FC)
private val backgroundDark = Color(0xFF111318)
private val onBackgroundDark = Color(0xFFE2E2E9)
private val surfaceDark = Color(0xFF111318)
private val onSurfaceDark = Color(0xFFE2E2E9)
private val surfaceVariantDark = Color(0xFF44474E)
private val onSurfaceVariantDark = Color(0xFFC4C6D0)
private val surfaceTintDark = Color(0xFFACC7FF)
private val inverseSurfaceDark = Color(0xFFE2E2E9)
private val inverseOnSurfaceDark = Color(0xFF2E3036)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val outlineDark = Color(0xFF8E9099)
private val outlineVariantDark = Color(0xFF44474E)
private val scrimDark = Color(0xFF000000)
private val surfaceBrightDark = Color(0xFF37393E)
private val surfaceContainerDark = Color(0xFF1E2025)
private val surfaceContainerHighDark = Color(0xFF282A2F)
private val surfaceContainerHighestDark = Color(0xFF33353A)
private val surfaceContainerLowDark = Color(0xFF1A1C20)
private val surfaceContainerLowestDark = Color(0xFF0C0E13)
private val surfaceDimDark = Color(0xFF111318)

internal val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    inversePrimary = inversePrimaryLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    surfaceTint = surfaceTintLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceDim = surfaceDimLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    inversePrimary = inversePrimaryDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    surfaceTint = surfaceTintDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceDim = surfaceDimDark,
)
