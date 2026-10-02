package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bewertung aus dem Detail-Sheet im Kartenstapel (#14): wie ein Wisch, genau einmal je Karte. */
class KartenSteuerungTest {

    private val steuerung = KartenSteuerung()
    private val karte = inserat("1")

    @Test
    fun `die Karte übernimmt die Anfrage genau einmal`() {
        steuerung.wischen(karte, Label.INTERESSANT)

        assertEquals(Label.INTERESSANT, steuerung.uebernehmen("1"))
        assertNull(steuerung.anfrage)
        assertNull(steuerung.uebernehmen("1"))
    }

    @Test
    fun `Neu ist im Stapel das aktuelle Label und tut nichts`() {
        steuerung.wischen(karte, Label.UNBEWERTET)
        assertNull(steuerung.anfrage)
    }

    @Test
    fun `eine andere Karte übernimmt die Anfrage nicht`() {
        steuerung.wischen(karte, Label.INTERESSANT)
        assertNull(steuerung.uebernehmen("2"))
        assertEquals(Label.INTERESSANT, steuerung.anfrage?.label)
    }

    @Test
    fun `fliegt die Karte schon, zählt ein zweites Segment nicht`() {
        steuerung.wischen(karte, Label.INTERESSANT)
        steuerung.uebernehmen("1")

        steuerung.wischen(karte, Label.UNINTERESSANT)

        assertNull(steuerung.anfrage)
    }

    @Test
    fun `nach Rückgängig fliegt die zurückgeholte Karte nicht erneut heraus`() {
        steuerung.wischen(karte, Label.INTERESSANT)
        steuerung.uebernehmen("1")
        steuerung.wischen(karte, Label.UNINTERESSANT) // zweites Segment während des Flugs

        steuerung.behalte(setOf("2")) // bewertet: Die Karte verlässt den Stapel
        steuerung.behalte(setOf("1", "2")) // Rückgängig: Sie ist wieder oben

        assertNull(steuerung.anfrage)
        assertNull(steuerung.uebernehmen("1"))
    }

    @Test
    fun `eine Anfrage für eine Karte, die nicht mehr im Stapel liegt, verfällt`() {
        steuerung.wischen(karte, Label.UNINTERESSANT)

        steuerung.behalte(setOf("2"))
        steuerung.behalte(setOf("1", "2"))

        assertNull(steuerung.anfrage)
    }

    @Test
    fun `nach dem Verlassen des Stapels lässt sich die Karte wieder aus dem Sheet bewerten`() {
        steuerung.wischen(karte, Label.INTERESSANT)
        steuerung.uebernehmen("1")
        steuerung.behalte(emptySet())
        steuerung.behalte(setOf("1"))

        steuerung.wischen(karte, Label.UNINTERESSANT)

        assertEquals(Label.UNINTERESSANT, steuerung.uebernehmen("1"))
    }

    @Test
    fun `verworfene Anfrage (Karte nicht oben) ist weg`() {
        steuerung.wischen(karte, Label.INTERESSANT)
        steuerung.verwerfen()
        assertNull(steuerung.anfrage)
    }
}
