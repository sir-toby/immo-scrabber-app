package de.immoscrabber.app.core.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BerechtigungsAnfrageTest {
    @Test
    fun `ab Android 13 wird einmal gefragt, wenn die Berechtigung fehlt`() {
        assertTrue(sollBenachrichtigungenAnfragen(sdkInt = 33, schonGefragt = false, erteilt = false))
    }

    @Test
    fun `wurde schon gefragt, wird nie wieder gefragt`() {
        assertFalse(sollBenachrichtigungenAnfragen(sdkInt = 36, schonGefragt = true, erteilt = false))
    }

    @Test
    fun `ist die Berechtigung schon erteilt, wird nicht gefragt`() {
        assertFalse(sollBenachrichtigungenAnfragen(sdkInt = 36, schonGefragt = false, erteilt = true))
    }

    @Test
    fun `vor Android 13 gibt es nichts zu fragen`() {
        assertFalse(sollBenachrichtigungenAnfragen(sdkInt = 32, schonGefragt = false, erteilt = false))
    }
}
