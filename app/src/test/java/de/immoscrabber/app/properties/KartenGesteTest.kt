package de.immoscrabber.app.properties

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstest für #51: Unter Last kamen beim schnellen Wischen nur Aufsetzen und Loslassen an,
 * ohne Bewegung dazwischen (Log: Press bei x=758, Release bei x=38). Das wurde als Tippen gewertet.
 */
class KartenGesteTest {

    private val slop = 20f

    @Test
    fun `Loslassen an derselben Stelle ist ein Tippen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        assertEquals(KartenGeste.Ende.Tippen, geste.up(Offset(500f, 700f), uptimeMillis = 80))
    }

    @Test
    fun `kleines Zittern innerhalb der Touch-Slop bleibt ein Tippen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(508f, 706f))
        assertEquals(KartenGeste.Ende.Tippen, geste.up(Offset(510f, 710f), uptimeMillis = 80))
    }

    @Test
    fun `Loslassen weit weg ohne Bewegung dazwischen ist ein Wisch, kein Tippen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(758f, 711f), uptimeMillis = 1_000)
        val ende = geste.up(Offset(38f, 731f), uptimeMillis = 1_150)
        assertEquals(KartenGeste.Ende.Sprung(dx = -720f, velocity = -4_800f), ende)
    }

    @Test
    fun `Bewegung nur innerhalb der Slop, dann weit weg losgelassen ist ein Wisch`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(100f, 700f), uptimeMillis = 0)
        geste.move(Offset(110f, 700f))
        val ende = geste.up(Offset(900f, 700f), uptimeMillis = 200)
        assertEquals(KartenGeste.Ende.Sprung(dx = 800f, velocity = 4_000f), ende)
    }

    @Test
    fun `senkrechtes Ziehen gehört dem Scrollen (Pull-to-Refresh)`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(505f, 800f))
        assertEquals(KartenGeste.Ende.Gescrollt, geste.up(Offset(510f, 900f), uptimeMillis = 200, step = Offset(5f, 100f)))
    }

    @Test
    fun `senkrechtes Ziehen mit seitlichem Abdriften bleibt Scrollen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(505f, 760f))
        geste.move(Offset(540f, 900f))
        assertEquals(KartenGeste.Ende.Gescrollt, geste.up(Offset(545f, 950f), uptimeMillis = 200))
    }

    @Test
    fun `erst senkrecht gezogen, dann waagrecht weit weg losgelassen bleibt Scrollen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(505f, 900f))
        assertEquals(KartenGeste.Ende.Gescrollt, geste.up(Offset(900f, 900f), uptimeMillis = 200, step = Offset(395f, 0f)))
    }

    @Test
    fun `senkrechter Sprung ohne Bewegung dazwischen ist kein Tippen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        assertEquals(KartenGeste.Ende.Verworfen, geste.up(Offset(505f, 1_000f), uptimeMillis = 150))
    }

    @Test
    fun `mit Bewegung über die Slop hinaus übernimmt das Ziehen`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(100f, 700f), uptimeMillis = 0)
        geste.move(Offset(300f, 705f))
        assertEquals(KartenGeste.Ende.Gezogen(restDx = 0f), geste.up(Offset(900f, 710f), uptimeMillis = 150))
    }

    @Test
    fun `nach dem Ziehen zählt auch die Strecke, die erst mit dem Loslassen ankam`() {
        // Log #51: letzte Bewegung bei x=443, Loslassen bei x=38 – die Karte federte zurück.
        val geste = KartenGeste(slop)
        geste.down(Offset(758f, 711f), uptimeMillis = 0)
        geste.move(Offset(443f, 722f))
        val ende = geste.up(Offset(38f, 731f), uptimeMillis = 150, step = Offset(-405f, 9f))
        assertEquals(KartenGeste.Ende.Gezogen(restDx = -405f), ende)
    }

    @Test
    fun `gleichzeitiges Aufsetzen und Loslassen teilt nicht durch null`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(800f, 700f), uptimeMillis = 500)
        val ende = geste.up(Offset(80f, 700f), uptimeMillis = 500)
        assertEquals(KartenGeste.Ende.Sprung(dx = -720f, velocity = -720_000f), ende)
    }

    @Test
    fun `jede Geste beginnt frisch`() {
        val geste = KartenGeste(slop)
        geste.down(Offset(100f, 700f), uptimeMillis = 0)
        geste.move(Offset(400f, 700f))
        geste.up(Offset(900f, 700f), uptimeMillis = 150)
        geste.down(Offset(500f, 700f), uptimeMillis = 1_000)
        assertEquals(KartenGeste.Ende.Tippen, geste.up(Offset(500f, 700f), uptimeMillis = 1_080))
    }

    // --- Long Press öffnet das Detail-Sheet (#14) ---

    private val timeout = 500L

    @Test
    fun `stillgehalten bis zum Timeout ist ein Long Press, genau einmal`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 1_000)
        assertFalse(geste.longPress(uptimeMillis = 1_499))
        assertTrue(geste.longPress(uptimeMillis = 1_500))
        assertFalse(geste.longPress(uptimeMillis = 1_600))
    }

    @Test
    fun `Zittern innerhalb der Slop verhindert den Long Press nicht`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(510f, 708f))
        assertTrue(geste.longPress(uptimeMillis = 500))
    }

    @Test
    fun `nach dem Long Press ist das Loslassen weder Tippen noch Wisch`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.longPress(uptimeMillis = 500)
        // Auch wenn der Finger danach noch wandert: Das Sheet ist offen, nichts wird bewertet.
        geste.move(Offset(900f, 700f))
        assertEquals(KartenGeste.Ende.LangGedrueckt(erstBeimLoslassen = false), geste.up(Offset(900f, 700f), uptimeMillis = 900))
    }

    @Test
    fun `waagrecht über die Slop bewegt vor dem Timeout ist ein Wisch, kein Long Press`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(560f, 700f))
        assertFalse(geste.longPress(uptimeMillis = 600))
        assertEquals(KartenGeste.Ende.Gezogen(restDx = 0f), geste.up(Offset(900f, 700f), uptimeMillis = 700))
    }

    @Test
    fun `senkrecht bewegt (Pull-to-Refresh) ist kein Long Press`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(500f, 760f))
        assertFalse(geste.longPress(uptimeMillis = 600))
    }

    @Test
    fun `zuletzt gemeldete Position außerhalb der Slop verhindert den Long Press`() {
        // Bewegung kam an, aber noch ohne Achsenentscheidung (diagonal knapp über der Slop zurück).
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.move(Offset(515f, 714f))
        assertFalse(geste.longPress(uptimeMillis = 600))
    }

    @Test
    fun `schneller Wisch ohne Bewegung dazwischen bleibt ein Sprung (#51)`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(758f, 711f), uptimeMillis = 1_000)
        assertFalse(geste.longPress(uptimeMillis = 1_150))
        assertEquals(KartenGeste.Ende.Sprung(dx = -720f, velocity = -4_800f), geste.up(Offset(38f, 731f), uptimeMillis = 1_150))
    }

    @Test
    fun `still losgelassen nach dem Timeout, ohne dass der Long Press ankam, öffnet beim Loslassen`() {
        // Unter Last kann das Loslassen vor dem Timer verarbeitet werden.
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        assertEquals(KartenGeste.Ende.LangGedrueckt(erstBeimLoslassen = true), geste.up(Offset(505f, 700f), uptimeMillis = 700))
    }

    @Test
    fun `kurz still losgelassen bleibt ein Tippen`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        assertEquals(KartenGeste.Ende.Tippen, geste.up(Offset(500f, 700f), uptimeMillis = 499))
    }

    @Test
    fun `weit weg losgelassen nach dem Timeout ohne Bewegung dazwischen bleibt ein Sprung`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(800f, 700f), uptimeMillis = 0)
        assertEquals(KartenGeste.Ende.Sprung(dx = -400f, velocity = -400f), geste.up(Offset(400f, 700f), uptimeMillis = 1_000))
    }

    @Test
    fun `der Long Press gilt nur für seine Geste`() {
        val geste = KartenGeste(slop, timeout)
        geste.down(Offset(500f, 700f), uptimeMillis = 0)
        geste.longPress(uptimeMillis = 500)
        geste.up(Offset(500f, 700f), uptimeMillis = 600)
        geste.down(Offset(500f, 700f), uptimeMillis = 1_000)
        assertEquals(KartenGeste.Ende.Tippen, geste.up(Offset(500f, 700f), uptimeMillis = 1_080))
    }
}
