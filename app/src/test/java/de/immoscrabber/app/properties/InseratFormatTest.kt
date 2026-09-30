package de.immoscrabber.app.properties

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
