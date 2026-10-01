package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.model.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BenachrichtigungsInhaltTest {

    private fun inhalt(type: PropertyType, count: Int, lines: List<String> = emptyList()) =
        BenachrichtigungsInhalt.aus(PushNachricht(type, count, lines))

    @Test
    fun `Titel im Plural`() {
        assertEquals("3 neue Häuser", inhalt(PropertyType.HOUSE, 3).titel)
        assertEquals("2 neue Wohnungen", inhalt(PropertyType.FLAT, 2).titel)
        assertEquals("12 neue Grundstücke", inhalt(PropertyType.SITE, 12).titel)
    }

    @Test
    fun `Titel im Singular`() {
        assertEquals("1 neues Haus", inhalt(PropertyType.HOUSE, 1).titel)
        assertEquals("1 neue Wohnung", inhalt(PropertyType.FLAT, 1).titel)
        assertEquals("1 neues Grundstück", inhalt(PropertyType.SITE, 1).titel)
    }

    @Test
    fun `bis zu drei Zeilen und der Rest als plus n weitere`() {
        val zeilen = listOf("Erlangen · 450.000 €", "Fürth", "Nürnberg · 140 m²")

        assertEquals(zeilen + "+ 4 weitere", inhalt(PropertyType.HOUSE, 7, zeilen).zeilen)
    }

    @Test
    fun `ohne Rest kein plus n weitere`() {
        assertEquals(listOf("Erlangen", "Fürth"), inhalt(PropertyType.FLAT, 2, listOf("Erlangen", "Fürth")).zeilen)
    }

    @Test
    fun `ohne Zeilen zählt alles als weitere`() {
        assertEquals(listOf("+ 5 weitere"), inhalt(PropertyType.SITE, 5).zeilen)
    }

    @Test
    fun `je Typ ein eigener Channel und eine eigene Benachrichtigung`() {
        val ids = PropertyType.entries.map { inhalt(it, 1) }
        assertEquals("neue_haeuser", ids[0].channelId)
        assertEquals("neue_wohnungen", ids[1].channelId)
        assertEquals("neue_grundstuecke", ids[2].channelId)
        assertEquals(3, ids.map { it.notificationId }.toSet().size)
        assertEquals(inhalt(PropertyType.HOUSE, 1).notificationId, inhalt(PropertyType.HOUSE, 9).notificationId)
        assertNotEquals(inhalt(PropertyType.HOUSE, 1).notificationId, inhalt(PropertyType.SITE, 1).notificationId)
    }
}
