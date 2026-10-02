package de.immoscrabber.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionLabelTest {

    @Test
    fun `Debug-Build mit Commit in Klammern`() {
        assertEquals("0.0.0-dev (a6ed8ea)", appVersionLabel("0.0.0-dev", gitSha = "a6ed8ea"))
        assertEquals("0.0.0-dev (a6ed8ea-dirty)", appVersionLabel("0.0.0-dev", gitSha = "a6ed8ea-dirty"))
    }

    @Test
    fun `Release ohne Commit nur die Version`() {
        assertEquals("1.1.0", appVersionLabel("1.1.0", gitSha = ""))
        assertEquals("1.1.0", appVersionLabel("1.1.0", gitSha = "  "))
    }
}
