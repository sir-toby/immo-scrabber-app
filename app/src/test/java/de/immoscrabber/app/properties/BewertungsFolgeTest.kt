package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Label
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Review zu #51: Die nächste Karte ist sofort oben, eine schnell gewischte Karte landet also vor
 * der langsamer fliegenden davor. Bewertet wird trotzdem in Wischreihenfolge, damit Snackbar und
 * „Rückgängig“ die zuletzt gewischte Karte meinen.
 */
class BewertungsFolgeTest {

    private data class Karte(val id: String, val price: Int = 100)

    private fun folge() = BewertungsFolge<Karte> { it.id }

    private val a = Karte("a")
    private val b = Karte("b")
    private val c = Karte("c")

    @Test
    fun `eine Karte wird bewertet, sobald sie gelandet ist`() {
        val folge = folge()
        folge.start(a)
        assertEquals(listOf(a to Label.INTERESSANT), folge.finish(a, Label.INTERESSANT))
    }

    @Test
    fun `landet die später gewischte Karte zuerst, wartet sie auf die davor`() {
        val folge = folge()
        folge.start(a)
        folge.start(b)
        assertEquals(emptyList<Pair<Karte, Label>>(), folge.finish(b, Label.UNINTERESSANT))
        assertEquals(
            listOf(a to Label.INTERESSANT, b to Label.UNINTERESSANT),
            folge.finish(a, Label.INTERESSANT),
        )
    }

    @Test
    fun `in Reihenfolge gelandete Karten gehen einzeln raus`() {
        val folge = folge()
        folge.start(a)
        folge.start(b)
        folge.start(c)
        assertEquals(listOf(a to Label.INTERESSANT), folge.finish(a, Label.INTERESSANT))
        assertEquals(emptyList<Pair<Karte, Label>>(), folge.finish(c, Label.INTERESSANT))
        assertEquals(
            listOf(b to Label.UNINTERESSANT, c to Label.INTERESSANT),
            folge.finish(b, Label.UNINTERESSANT),
        )
    }

    @Test
    fun `eine nie gestartete Karte wird sofort bewertet`() {
        val folge = folge()
        assertEquals(listOf(a to Label.UNINTERESSANT), folge.finish(a, Label.UNINTERESSANT))
    }

    @Test
    fun `geänderter Inhalt derselben Karte während des Flugs blockiert nichts`() {
        // Pull-to-Refresh liefert dieselbe Karte mit neuem Preis, während sie fliegt.
        val folge = folge()
        folge.start(a)
        folge.start(b)
        val aNeu = a.copy(price = 90)
        assertEquals(listOf(aNeu to Label.INTERESSANT), folge.finish(aNeu, Label.INTERESSANT))
        assertEquals(listOf(b to Label.UNINTERESSANT), folge.finish(b, Label.UNINTERESSANT))
    }

    @Test
    fun `verlässt die vorderste Karte den Stapel, gehen die Bewertungen dahinter raus`() {
        val folge = folge()
        folge.start(a)
        folge.start(b)
        assertEquals(emptyList<Pair<Karte, Label>>(), folge.finish(b, Label.UNINTERESSANT))
        // Refresh: a ist nicht mehr im Stapel, ihr Flug endet womöglich nie.
        assertEquals(listOf(b to Label.UNINTERESSANT), folge.retain(setOf("b", "c")))
        folge.start(c)
        assertEquals(listOf(c to Label.INTERESSANT), folge.finish(c, Label.INTERESSANT))
    }
}
