package de.immoscrabber.app.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmoColorsTest {

    @Test
    fun `light energy classes use the web colors`() {
        val colors = ImmoColors.Light
        assertEquals(Color(0xFF4CAF50), colors.energyClass("A+"))
        assertEquals(Color(0xFF4CAF50), colors.energyClass("A"))
        assertEquals(Color(0xFF8BC34A), colors.energyClass("B"))
        assertEquals(Color(0xFFCDDC39), colors.energyClass("C"))
        assertEquals(Color(0xFFFFEB3B), colors.energyClass("D"))
        assertEquals(Color(0xFFFFC107), colors.energyClass("E"))
        assertEquals(Color(0xFFFF9800), colors.energyClass("F"))
        assertEquals(Color(0xFFFF5722), colors.energyClass("G"))
        assertEquals(Color(0xFFF44336), colors.energyClass("H"))
    }

    @Test
    fun `energy class lookup tolerates case and whitespace`() {
        assertEquals(Color(0xFF4CAF50), ImmoColors.Light.energyClass(" a+ "))
    }

    @Test
    fun `dark energy classes are toned down variants`() {
        assertEquals(Color(0xFF499049), ImmoColors.Dark.energyClass("A+"))
        assertEquals(Color(0xFFD1483B), ImmoColors.Dark.energyClass("H"))
    }

    @Test
    fun `unknown or missing energy class has no color`() {
        assertNull(ImmoColors.Light.energyClass("I"))
        assertNull(ImmoColors.Light.energyClass(null))
    }
}
