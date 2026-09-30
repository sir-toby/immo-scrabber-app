package de.immoscrabber.app.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class BaseUrlTest {
    private fun release(input: String) = normalizeBaseUrl(input, allowLocalCleartext = false)
    private fun debug(input: String) = normalizeBaseUrl(input, allowLocalCleartext = true)

    @Test
    fun `ohne Schema wird https ergänzt und api angehängt`() {
        assertEquals(BaseUrlResult.Valid("https://immo.example.com/api/"), release("immo.example.com"))
    }

    @Test
    fun `fehlender Schrägstrich hinter api wird ergänzt`() {
        assertEquals(BaseUrlResult.Valid("https://immo.example.com/api/"), release("https://immo.example.com/api"))
    }

    @Test
    fun `vollständige URL bleibt unverändert`() {
        assertEquals(BaseUrlResult.Valid("https://immo.example.com/api/"), release("https://immo.example.com/api/"))
    }

    @Test
    fun `Pfad unter einem Präfix bekommt api angehängt`() {
        assertEquals(BaseUrlResult.Valid("https://example.com/immo/api/"), release("https://example.com/immo/"))
    }

    @Test
    fun `Leerzeichen und Port bleiben korrekt`() {
        assertEquals(BaseUrlResult.Valid("https://example.com:8443/api/"), release("  example.com:8443  "))
    }

    @Test
    fun `Schema in Großbuchstaben wird erkannt`() {
        assertEquals(BaseUrlResult.Valid("https://example.com/api/"), release("HTTPS://Example.com"))
    }

    @Test
    fun `http ist im Release nie erlaubt`() {
        assertEquals(BaseUrlResult.HttpsRequired, release("http://example.com"))
        assertEquals(BaseUrlResult.HttpsRequired, release("http://10.0.2.2:5000"))
    }

    @Test
    fun `im Debug ist http nur für Emulator-Host und localhost erlaubt`() {
        assertEquals(BaseUrlResult.Valid("http://10.0.2.2:5000/api/"), debug("http://10.0.2.2:5000"))
        assertEquals(BaseUrlResult.Valid("http://localhost:5000/api/"), debug("http://localhost:5000/api"))
        assertEquals(BaseUrlResult.HttpsRequired, debug("http://example.com"))
    }

    @Test
    fun `leere oder kaputte Eingabe ist ungültig`() {
        assertEquals(BaseUrlResult.Invalid, release(""))
        assertEquals(BaseUrlResult.Invalid, release("   "))
        assertEquals(BaseUrlResult.Invalid, release("ftp://example.com"))
        assertEquals(BaseUrlResult.Invalid, release("https://"))
        assertEquals(BaseUrlResult.Invalid, release("exa mple.com"))
    }
}
