package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.model.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Data-Message, wie sie `scraper/notifier.py` (Backend #141) schickt. */
class PushNachrichtTest {

    @Test
    fun `liest Typ, Anzahl und Zeilen aus der Data-Message`() {
        val nachricht = PushNachricht.parse(
            mapOf(
                "type" to "house",
                "count" to "5",
                "lines" to """["Erlangen · 450.000 € · 140 m²","Fürth · 380.000 €","Nürnberg"]""",
            ),
        )

        assertEquals(
            PushNachricht(
                PropertyType.HOUSE,
                5,
                listOf("Erlangen · 450.000 € · 140 m²", "Fürth · 380.000 €", "Nürnberg"),
            ),
            nachricht,
        )
    }

    @Test
    fun `alle drei Typen werden erkannt`() {
        assertEquals(PropertyType.FLAT, PushNachricht.parse(mapOf("type" to "flat", "count" to "1"))?.type)
        assertEquals(PropertyType.SITE, PushNachricht.parse(mapOf("type" to "site", "count" to "1"))?.type)
    }

    @Test
    fun `ohne oder mit unbekanntem Typ gibt es keine Nachricht`() {
        assertNull(PushNachricht.parse(mapOf("count" to "3", "lines" to "[]")))
        assertNull(PushNachricht.parse(mapOf("type" to "castle", "count" to "3")))
    }

    @Test
    fun `ohne gültige positive Anzahl gibt es keine Nachricht`() {
        assertNull(PushNachricht.parse(mapOf("type" to "house")))
        assertNull(PushNachricht.parse(mapOf("type" to "house", "count" to "drei")))
        assertNull(PushNachricht.parse(mapOf("type" to "house", "count" to "0")))
        assertNull(PushNachricht.parse(mapOf("type" to "house", "count" to "-2")))
    }

    @Test
    fun `fehlende oder kaputte Zeilen ergeben eine Nachricht ohne Zeilen`() {
        assertEquals(emptyList<String>(), PushNachricht.parse(mapOf("type" to "house", "count" to "2"))?.lines)
        assertEquals(
            emptyList<String>(),
            PushNachricht.parse(mapOf("type" to "house", "count" to "2", "lines" to "kein json"))?.lines,
        )
        assertEquals(
            emptyList<String>(),
            PushNachricht.parse(mapOf("type" to "house", "count" to "2", "lines" to """{"a":1}"""))?.lines,
        )
    }

    @Test
    fun `nimmt nur nichtleere Text-Zeilen und höchstens drei`() {
        val nachricht = PushNachricht.parse(
            mapOf("type" to "flat", "count" to "9", "lines" to """["A", 42, null, "  ", ["x"], "B", "C", "D"]"""),
        )

        assertEquals(listOf("A", "B", "C"), nachricht?.lines)
    }
}
