package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InseratFormatTest {

    @Test
    fun `Preis deutsch mit Tausenderpunkt und Euro`() {
        assertEquals("450.000 €", formatPrice(450_000.0))
        assertEquals("1.250.000 €", formatPrice(1_250_000.0))
        assertEquals("999 €", formatPrice(999.4))
    }

    @Test
    fun `ohne Preis Preis auf Anfrage`() {
        assertEquals("Preis auf Anfrage", formatPrice(null))
        assertEquals("Preis auf Anfrage", formatPrice(0.0))
    }

    @Test
    fun `Eckdaten eines Hauses vollständig`() {
        val haus = inserat("1", rooms = 5.0, livingArea = 140.0, plotArea = 600.0, constructionYear = 1978)
        assertEquals("5 Zi · 140 m² Wfl. · 600 m² Grundst. · Bj. 1978", formatFacts(haus))
    }

    @Test
    fun `fehlende Eckdaten fallen weg, halbe Zimmer mit Komma, große Flächen mit Punkt`() {
        val haus = inserat("1", rooms = 4.5, plotArea = 1_200.4)
        assertEquals("4,5 Zi · 1.200 m² Grundst.", formatFacts(haus))
        assertEquals("", formatFacts(inserat("2")))
    }

    @Test
    fun `Eckdaten einer Wohnung ohne Grundstück, Fläche ohne Zusatz`() {
        val wohnung = inserat(
            "1", type = PropertyType.FLAT, rooms = 3.0, livingArea = 85.0, plotArea = 400.0, constructionYear = 1995,
        )
        assertEquals("3 Zi · 85 m² · Bj. 1995", formatFacts(wohnung))
    }

    @Test
    fun `fehlende Eckdaten einer Wohnung fallen weg`() {
        assertEquals("2,5 Zi · Bj. 1995", formatFacts(inserat("1", type = PropertyType.FLAT, rooms = 2.5, constructionYear = 1995)))
        assertEquals("1.050 m²", formatFacts(inserat("2", type = PropertyType.FLAT, livingArea = 1_050.0)))
        assertEquals("", formatFacts(inserat("3", type = PropertyType.FLAT)))
    }

    @Test
    fun `Eckdaten eines Grundstücks mit Preis pro m²`() {
        val grundstueck = inserat("1", type = PropertyType.SITE, price = 250_000.0, plotArea = 600.0)
        assertEquals("600 m² · 417 €/m²", formatFacts(grundstueck))
    }

    @Test
    fun `Grundstück zeigt nur Fläche und Preis pro m², Zimmer und Baujahr nie`() {
        val grundstueck = inserat(
            "1", type = PropertyType.SITE, price = 1_500_000.0, plotArea = 1_000.0,
            rooms = 3.0, livingArea = 120.0, constructionYear = 2000,
        )
        assertEquals("1.000 m² · 1.500 €/m²", formatFacts(grundstueck))
    }

    @Test
    fun `Preis pro m² kaufmännisch gerundet`() {
        // 249.900 € / 600 m² = 416,5 €/m²; die Standardrundung (HALF_EVEN) ergäbe „416“.
        val grundstueck = inserat("1", type = PropertyType.SITE, price = 249_900.0, plotArea = 600.0)
        assertEquals("600 m² · 417 €/m²", formatFacts(grundstueck))
    }

    @Test
    fun `Preis, Fläche und Zimmer ebenfalls kaufmännisch gerundet`() {
        assertEquals("999 €", formatPrice(998.5))
        assertEquals("85 m²", formatFacts(inserat("1", type = PropertyType.FLAT, livingArea = 84.5)))
        assertEquals("2,3 Zi", formatFacts(inserat("2", type = PropertyType.FLAT, rooms = 2.25)))
    }

    @Test
    fun `Wohnfläche von 0 m² fällt bei Haus und Wohnung weg`() {
        assertEquals("5 Zi · Bj. 1978", formatFacts(inserat("1", rooms = 5.0, livingArea = 0.0, constructionYear = 1978)))
        assertEquals(
            "3 Zi · Bj. 1995",
            formatFacts(inserat("2", type = PropertyType.FLAT, rooms = 3.0, livingArea = 0.0, constructionYear = 1995)),
        )
    }

    @Test
    fun `Preis pro m² nur mit Preis und Fläche`() {
        assertEquals("600 m²", formatFacts(inserat("1", type = PropertyType.SITE, plotArea = 600.0)))
        assertEquals("600 m²", formatFacts(inserat("2", type = PropertyType.SITE, price = 0.0, plotArea = 600.0)))
        assertEquals("", formatFacts(inserat("3", type = PropertyType.SITE, price = 250_000.0)))
        assertEquals("", formatFacts(inserat("4", type = PropertyType.SITE, price = 250_000.0, plotArea = 0.0)))
    }

    @Test
    fun `Ortszeile PLZ Ort und Eckdaten, fehlende Teile fallen weg`() {
        assertEquals(
            "91054 Erlangen · 5 Zi · Bj. 1978",
            formatPlaceAndFacts(inserat("1", zipCode = "91054", city = "Erlangen", rooms = 5.0, constructionYear = 1978)),
        )
        assertEquals("Erlangen", formatPlaceAndFacts(inserat("2", city = "Erlangen")))
        assertEquals("5 Zi", formatPlaceAndFacts(inserat("3", zipCode = " ", rooms = 5.0)))
    }

    @Test
    fun `Bild-URL https bleibt, http wird https`() {
        assertEquals("https://example.com/a.jpg", thumbnailUrl("https://example.com/a.jpg"))
        assertEquals("https://example.com/a.jpg", thumbnailUrl("http://example.com/a.jpg"))
        assertEquals("https://example.com/a.jpg", thumbnailUrl("  HTTP://example.com/a.jpg "))
    }

    @Test
    fun `relative, data- und leere Bild-URLs ergeben den Platzhalter`() {
        assertNull(thumbnailUrl(null))
        assertNull(thumbnailUrl(""))
        assertNull(thumbnailUrl("/images/a.jpg"))
        assertNull(thumbnailUrl("//cdn.example.com/a.jpg"))
        assertNull(thumbnailUrl("data:image/png;base64,iVBORw0KGgo="))
        assertNull(thumbnailUrl("https://"))
    }
}
