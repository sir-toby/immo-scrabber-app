package de.immoscrabber.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalImmoColors = staticCompositionLocalOf { ImmoColors.Light }

/**
 * App-Theme: festes M3-Schema, keine Dynamic Color, Dark Mode folgt dem System.
 * Fachfarben über [MaterialTheme.immoColors].
 */
@Composable
fun ImmoFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalImmoColors provides if (darkTheme) ImmoColors.Dark else ImmoColors.Light) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content,
        )
    }
}

/** Fachfarben (Bewertung, Energieklassen) passend zu hell/dunkel. */
val MaterialTheme.immoColors: ImmoColors
    @Composable
    @ReadOnlyComposable
    get() = LocalImmoColors.current
