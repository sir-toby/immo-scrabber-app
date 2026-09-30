package de.immoscrabber.app.properties

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
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
}
