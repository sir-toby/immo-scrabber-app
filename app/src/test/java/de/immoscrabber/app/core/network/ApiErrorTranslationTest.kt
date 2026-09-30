package de.immoscrabber.app.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Die uneinheitlichen Fehlerformate des Backends (`Error`, `error`, `msg`, HTML) kommen
 * alle als [ApiError] an. Geprüft am Beispiel `GET /preferences`; die Übersetzung ist für
 * alle Endpoints dieselbe.
 */
class ApiErrorTranslationTest {
    @get:Rule val mock = MockApiRule()

    private suspend fun errorFor(response: MockResponse): ApiError {
        mock.enqueue(response)
        val result = mock.api.suchprofile()
        assertTrue("erwartet Failure, war $result", result is ApiResult.Failure)
        return (result as ApiResult.Failure).error
    }

    @Test
    fun `abgelaufenes Token (401 msg) ist Sitzungsende`() = runTest {
        assertEquals(ApiError.SessionExpired, errorFor(jsonResponse(401, "errors/jwt_expired_401.json")))
    }

    @Test
    fun `fehlender Auth-Header (401 msg) ist Sitzungsende`() = runTest {
        assertEquals(ApiError.SessionExpired, errorFor(jsonResponse(401, "errors/jwt_missing_header_401.json")))
    }

    @Test
    fun `kaputtes Token (422 msg) ist Sitzungsende`() = runTest {
        assertEquals(ApiError.SessionExpired, errorFor(jsonResponse(422, "errors/jwt_invalid_422.json")))
    }

    @Test
    fun `400 mit großem Error liefert den Servertext`() = runTest {
        assertEquals(
            ApiError.BadRequest("Keine Suchparameter gefunden"),
            errorFor(jsonResponse(400, "properties/results_400_kein_suchprofil.json")),
        )
    }

    @Test
    fun `400 mit kleinem error liefert den Servertext`() = runTest {
        assertEquals(
            ApiError.BadRequest("Maximum of 10 search preferences allowed per user."),
            errorFor(jsonResponse(400, "preferences/preferences_limit_400.json")),
        )
    }

    @Test
    fun `400 ohne JSON-Body hat keinen Servertext`() = runTest {
        assertEquals(
            ApiError.BadRequest(null),
            errorFor(MockResponse().setResponseCode(400).setBody("<h1>Bad Request</h1>")),
        )
    }

    @Test
    fun `404 mit error ist HTTP-Fehler mit Code`() = runTest {
        assertEquals(ApiError.Http(404), errorFor(jsonResponse(404, "preferences/preference_404.json")))
    }

    @Test
    fun `HTML-Fehlerseite bei 500 ist HTTP-Fehler mit Code`() = runTest {
        val html = MockResponse()
            .setResponseCode(500)
            .setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody(fixture("errors/internal_server_error_500.html"))
        assertEquals(ApiError.Http(500), errorFor(html))
    }

    @Test
    fun `abgebrochene Verbindung ist Netzwerkfehler`() = runTest {
        val error = errorFor(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertTrue("war $error", error is ApiError.Network)
    }

    @Test
    fun `unlesbare Erfolgsantwort ist ungültige Antwort`() = runTest {
        val error = errorFor(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody("<html>Wartung</html>"),
        )
        assertTrue("war $error", error is ApiError.InvalidResponse)
    }
}
