package de.immoscrabber.app.properties

import org.junit.Assert.assertEquals
import org.junit.Test

class KartenstapelFormatTest {

    @Test
    fun `am Ende der Liste die genaue Zahl übrig`() {
        assertEquals("7 übrig", formatRemaining(count = 7, moreAvailable = false))
        assertEquals("1 übrig", formatRemaining(count = 1, moreAvailable = false))
    }

    @Test
    fun `solange weitere Seiten existieren 20+ übrig`() {
        assertEquals("20+ übrig", formatRemaining(count = 3, moreAvailable = true))
        assertEquals("20+ übrig", formatRemaining(count = 35, moreAvailable = true))
    }
}
