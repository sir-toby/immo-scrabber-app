package de.immoscrabber.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Feste Fachfarben, unabhängig vom Seed, je hell/dunkel (Entscheidung #20).
 * Zugriff in Compose über `MaterialTheme.immoColors`. Herleitung siehe docs/theme.md.
 */
@Immutable
class ImmoColors private constructor(
    /** Bewertung „interessant“ (Favorit): Wisch-Hintergrund nach rechts. */
    val interessant: Color,
    val onInteressant: Color,
    /** Bewertung „uninteressant“ (Archiv): Wisch-Hintergrund nach links. */
    val uninteressant: Color,
    val onUninteressant: Color,
    /** Text auf Energieklassen-Badges (wie im Web: schwarz). */
    val onEnergyClass: Color,
    private val energyClasses: Map<String, Color>,
) {
    /** Badge-Farbe einer Energieeffizienzklasse `A+` … `H`; `null` für unbekannte/fehlende Klassen. */
    fun energyClass(energyClass: String?): Color? =
        energyClass?.trim()?.uppercase()?.let(energyClasses::get)

    companion object {
        val Light = ImmoColors(
            // Ton 40/100 der Paletten aus #4CAF50 bzw. #F44336
            interessant = Color(0xFF006E1C),
            onInteressant = Color(0xFFFFFFFF),
            uninteressant = Color(0xFFBB1614),
            onUninteressant = Color(0xFFFFFFFF),
            onEnergyClass = Color.Black,
            // Web-Skala aus frontend/src/style.css (A und A+ teilen sich die Farbe)
            energyClasses = energyScale(
                0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B,
                0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFFF44336,
            ),
        )

        val Dark = ImmoColors(
            // Ton 80/20
            interessant = Color(0xFF78DC77),
            onInteressant = Color(0xFF00390A),
            uninteressant = Color(0xFFFFB4A9),
            onUninteressant = Color(0xFF690002),
            onEnergyClass = Color.Black,
            // Gleicher Farbton, Chroma ×0,8, Ton −10 (begrenzt auf 50…80): blendet weniger,
            // schwarzer Text bleibt lesbar.
            energyClasses = energyScale(
                0xFF499049, 0xFF7BA54B, 0xFFB3BE48, 0xFFD8C94A,
                0xFFD8A830, 0xFFD48424, 0xFFCD4D26, 0xFFD1483B,
            ),
        )

        private fun energyScale(vararg abcdefgh: Long): Map<String, Color> {
            val colors = abcdefgh.map(::Color)
            return mapOf("A+" to colors[0]) + "ABCDEFGH".map(Char::toString).zip(colors)
        }
    }
}
