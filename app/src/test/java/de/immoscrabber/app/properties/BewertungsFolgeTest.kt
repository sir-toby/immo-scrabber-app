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

    @Test
    fun `eine Karte wird bewertet, sobald sie gelandet ist`() {
        val folge = BewertungsFolge<String>()
        folge.start("a")
        assertEquals(listOf("a" to Label.INTERESSANT), folge.finish("a", Label.INTERESSANT))
    }

    @Test
    fun `landet die später gewischte Karte zuerst, wartet sie auf die davor`() {
        val folge = BewertungsFolge<String>()
        folge.start("a")
        folge.start("b")
        assertEquals(emptyList<Pair<String, Label>>(), folge.finish("b", Label.UNINTERESSANT))
        assertEquals(
            listOf("a" to Label.INTERESSANT, "b" to Label.UNINTERESSANT),
            folge.finish("a", Label.INTERESSANT),
        )
    }

    @Test
    fun `in Reihenfolge gelandete Karten gehen einzeln raus`() {
        val folge = BewertungsFolge<String>()
        folge.start("a")
        folge.start("b")
        folge.start("c")
        assertEquals(listOf("a" to Label.INTERESSANT), folge.finish("a", Label.INTERESSANT))
        assertEquals(emptyList<Pair<String, Label>>(), folge.finish("c", Label.INTERESSANT))
        assertEquals(
            listOf("b" to Label.UNINTERESSANT, "c" to Label.INTERESSANT),
            folge.finish("b", Label.UNINTERESSANT),
        )
    }

    @Test
    fun `eine nie gestartete Karte wird sofort bewertet`() {
        val folge = BewertungsFolge<String>()
        assertEquals(listOf("x" to Label.UNINTERESSANT), folge.finish("x", Label.UNINTERESSANT))
    }
}
