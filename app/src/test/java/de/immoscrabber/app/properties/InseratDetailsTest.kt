package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Reine Logik des Detail-Sheets (#14): Gefunden, Adresse, Eckdaten-Chips, Teilen, Umschalter. */
class InseratDetailsTest {

    // 2. Oktober 2026, 12:00 in Berlin (Sommerzeit, UTC+2).
    private val clock = Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneId.of("Europe/Berlin"))

    // --- Gefunden ---

    @Test
    fun `heute, gestern, vor n Tagen`() {
        assertEquals("Gefunden heute", formatGefunden("2026-10-02T06:00:00", clock))
        assertEquals("Gefunden gestern", formatGefunden("2026-10-01T09:00:00", clock))
        assertEquals("Gefunden vor 3 Tagen", formatGefunden("2026-09-29T12:00:00.177763", clock))
        assertEquals("Gefunden vor 6 Tagen", formatGefunden("2026-09-26T12:00:00", clock))
    }

    @Test
    fun `ab 7 Tagen als Datum`() {
        assertEquals("Gefunden am 25.09.2026", formatGefunden("2026-09-25T12:00:00", clock))
        assertEquals("Gefunden am 03.01.2025", formatGefunden("2025-01-03T12:00:00", clock))
    }

    @Test
    fun `der Zeitstempel ist UTC, der Tag zählt in der Zeitzone des Geräts`() {
        // 22:30 UTC am 1.10. ist 00:30 am 2.10. in Berlin.
        assertEquals("Gefunden heute", formatGefunden("2026-10-01T22:30:00", clock))
        assertEquals("Gefunden gestern", formatGefunden("2026-10-01T21:30:00", clock))
    }

    @Test
    fun `mit Leerzeichen statt T und ohne Sekundenbruchteile`() {
        assertEquals("Gefunden gestern", formatGefunden("2026-10-01 09:00:00", clock))
    }

    @Test
    fun `in der Zukunft (Uhren nicht synchron) gilt heute`() {
        assertEquals("Gefunden heute", formatGefunden("2026-10-03T12:00:00", clock))
    }

    @Test
    fun `fehlend oder unlesbar fällt die Zeile weg`() {
        assertNull(formatGefunden(null, clock))
        assertNull(formatGefunden("", clock))
        assertNull(formatGefunden("gestern", clock))
    }

    @Test
    fun `genauer Zeitpunkt in der Zeitzone des Geräts`() {
        // 12:32 UTC ist 14:32 in Berlin (Sommerzeit).
        assertEquals("Gefunden am 28.09.2026 um 14:32", formatGefundenGenau("2026-09-28T12:32:59.177763", clock.zone))
        // Über die Tagesgrenze: 22:05 UTC am 1.10. ist 00:05 am 2.10.
        assertEquals("Gefunden am 02.10.2026 um 00:05", formatGefundenGenau("2026-10-01 22:05:00", clock.zone))
        // Winterzeit: UTC+1.
        assertEquals("Gefunden am 03.01.2025 um 09:00", formatGefundenGenau("2025-01-03T08:00:00", clock.zone))
    }

    @Test
    fun `genauer Zeitpunkt fehlend oder unlesbar ohne Tooltip`() {
        assertNull(formatGefundenGenau(null, clock.zone))
        assertNull(formatGefundenGenau(" ", clock.zone))
        assertNull(formatGefundenGenau("gestern", clock.zone))
    }

    // --- Adresse ---

    @Test
    fun `Adresse aus Straße, Hausnummer, PLZ und Ort`() {
        assertEquals("Hauptstr. 5, 91054 Erlangen", formatAddress(adresse("Hauptstr.", "5", "91054", "Erlangen")))
    }

    @Test
    fun `fehlende Teile fallen weg`() {
        assertEquals("Hauptstr., 91054 Erlangen", formatAddress(adresse("Hauptstr.", null, "91054", "Erlangen")))
        assertEquals("91054 Erlangen", formatAddress(adresse(null, "5", "91054", "Erlangen")))
        assertEquals("Hauptstr. 5, Erlangen", formatAddress(adresse("Hauptstr.", "5", null, "Erlangen")))
        assertEquals("Hauptstr. 5", formatAddress(adresse("Hauptstr.", "5", " ", null)))
        assertEquals("", formatAddress(adresse(" ", " ", null, "")))
    }

    private fun adresse(street: String?, houseNumber: String?, zipCode: String?, city: String?) =
        inserat("1").copy(street = street, houseNumber = houseNumber, zipCode = zipCode, city = city)

    // --- Eckdaten-Chips ---

    @Test
    fun `Haus mit allen Eckdaten, Quadratmeterpreis auf die Wohnfläche`() {
        val haus = inserat(
            "1", price = 450_000.0, rooms = 5.0, livingArea = 140.0, plotArea = 600.0, constructionYear = 1978,
        )
        assertEquals(
            listOf(
                Fact(FactKind.ROOMS, "5 Zi"),
                Fact(FactKind.LIVING_AREA, "140 m² Wfl."),
                Fact(FactKind.PLOT_AREA, "600 m² Grundst."),
                Fact(FactKind.CONSTRUCTION_YEAR, "Bj. 1978"),
                Fact(FactKind.PRICE_PER_SQUARE_METER, "3.214 €/m²"),
            ),
            detailFacts(haus),
        )
    }

    @Test
    fun `Wohnung ohne Grundstück, Quadratmeterpreis auf die Wohnfläche`() {
        val wohnung = inserat(
            "1", type = PropertyType.FLAT, price = 255_000.0, rooms = 3.0, livingArea = 85.0, plotArea = 400.0,
            constructionYear = 1995,
        )
        assertEquals(
            listOf(
                Fact(FactKind.ROOMS, "3 Zi"),
                Fact(FactKind.LIVING_AREA, "85 m²"),
                Fact(FactKind.CONSTRUCTION_YEAR, "Bj. 1995"),
                Fact(FactKind.PRICE_PER_SQUARE_METER, "3.000 €/m²"),
            ),
            detailFacts(wohnung),
        )
    }

    @Test
    fun `Grundstück nur Fläche und Quadratmeterpreis auf die Fläche`() {
        val grundstueck = inserat("1", type = PropertyType.SITE, price = 250_000.0, rooms = 2.0, plotArea = 600.0)
        assertEquals(
            listOf(Fact(FactKind.PLOT_AREA, "600 m²"), Fact(FactKind.PRICE_PER_SQUARE_METER, "417 €/m²")),
            detailFacts(grundstueck),
        )
    }

    @Test
    fun `fehlende Werte fallen weg, ohne Preis oder Fläche kein Quadratmeterpreis`() {
        assertEquals(listOf(Fact(FactKind.ROOMS, "4 Zi")), detailFacts(inserat("1", rooms = 4.0)))
        assertEquals(
            listOf(Fact(FactKind.PLOT_AREA, "600 m² Grundst.")),
            detailFacts(inserat("1", price = 300_000.0, plotArea = 600.0)),
        )
        assertEquals(emptyList<Fact>(), detailFacts(inserat("1", type = PropertyType.SITE, price = 1.0, plotArea = 0.0)))
    }

    // --- Teilen ---

    @Test
    fun `Teilen mit dem Link als Inhalt und dem Titel als Betreff`() {
        val inserat = inserat("1", url = "  https://example.org/expose/1 ").copy(title = " Haus am See ")
        assertEquals(ShareContent(subject = "Haus am See", text = "https://example.org/expose/1"), shareContent(inserat))
    }

    @Test
    fun `ohne Titel ohne Betreff, ohne Link gibt es nichts zu teilen`() {
        assertEquals(
            ShareContent(subject = null, text = "https://example.org/1"),
            shareContent(inserat("1", url = "https://example.org/1").copy(title = " ")),
        )
        assertNull(shareContent(inserat("1", url = null)))
        assertNull(shareContent(inserat("1", url = "  ")))
    }

    // --- Umschalter ---

    @Test
    fun `Umschalter in der Reihenfolge Neu, Favorit, Archiv`() {
        assertEquals(listOf(Label.UNBEWERTET, Label.INTERESSANT, Label.UNINTERESSANT), bewertungsSegmente)
    }

    @Test
    fun `ein anderes Segment bewertet, das aktuelle nicht`() {
        assertEquals(Label.INTERESSANT, segmentWechsel(current = Label.UNBEWERTET, selected = Label.INTERESSANT))
        assertEquals(Label.UNBEWERTET, segmentWechsel(current = Label.UNINTERESSANT, selected = Label.UNBEWERTET))
        assertNull(segmentWechsel(current = Label.INTERESSANT, selected = Label.INTERESSANT))
    }

    // --- Sheet folgt der Liste ---

    @Test
    fun `das Sheet zeigt das Inserat aus der aktuellen Liste, verlässt es die Liste, schließt es`() {
        val items = listOf(inserat("1"), inserat("2", Label.INTERESSANT))
        assertEquals(items[1], sheetInserat(items, "2"))
        assertNull(sheetInserat(items, "3"))
        assertNull(sheetInserat(items, null))
    }

    // --- Inserat-ID ---

    @Test
    fun `lange ID gekürzt, kurze ganz`() {
        assertEquals("ID 472f14b1-…", formatShortId("472f14b1-9c3e-4d2a-8f00-123456789abc"))
        assertEquals("ID 4711", formatShortId("4711"))
        assertEquals("ID 472f14b1-", formatShortId("472f14b1-"))
    }

    @Test
    fun `eigene Rückmeldung zum Kopieren nur unter Android 13`() {
        assertTrue(zeigeKopiertHinweis(sdkInt = 32))
        assertFalse(zeigeKopiertHinweis(sdkInt = 33))
        assertFalse(zeigeKopiertHinweis(sdkInt = 36))
    }
}
