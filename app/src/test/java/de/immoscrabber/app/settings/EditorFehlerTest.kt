package de.immoscrabber.app.settings

import de.immoscrabber.app.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Fehler beim Speichern und Löschen (Entscheidung #8); die Texte stehen in den String-Ressourcen. */
class EditorFehlerTest {

    @Test
    fun `500 beim Speichern heißt Ort nicht gefunden`() {
        assertEquals(EditorFehler.OrtNichtGefunden, speicherFehler(ApiError.Http(500)))
    }

    @Test
    fun `400 zeigt den Text des Servers`() {
        assertEquals(
            EditorFehler.Server("Maximum of 10 search preferences allowed per user."),
            speicherFehler(ApiError.BadRequest("Maximum of 10 search preferences allowed per user.")),
        )
    }

    @Test
    fun `400 ohne Servertext und andere HTTP-Fehler sind unerwartet, mit Code`() {
        assertEquals(EditorFehler.Unerwartet(httpCode = 400), speicherFehler(ApiError.BadRequest(null)))
        assertEquals(EditorFehler.Unerwartet(httpCode = 404), speicherFehler(ApiError.Http(404)))
        assertEquals(EditorFehler.Unerwartet(httpCode = null), speicherFehler(ApiError.InvalidResponse(RuntimeException())))
    }

    @Test
    fun `Netzwerkfehler heißt Server nicht erreichbar`() {
        assertEquals(EditorFehler.NichtErreichbar, speicherFehler(ApiError.Network(IOException())))
        assertEquals(EditorFehler.NichtErreichbar, loeschFehler(ApiError.Network(IOException())))
    }

    @Test
    fun `beim Löschen ist 500 kein Ortsproblem`() {
        assertEquals(EditorFehler.Unerwartet(httpCode = 500), loeschFehler(ApiError.Http(500)))
    }
}
