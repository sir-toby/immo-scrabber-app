package de.immoscrabber.app.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthApiTest {
    @get:Rule val mock = MockApiRule()

    @Test
    fun `login schickt Zugangsdaten als JSON und liefert das Token-Paar`() = runTest {
        mock.enqueue(jsonResponse(200, "auth/login_200.json"))

        val result = mock.api.login("app-test", "geheim")

        assertEquals(
            ApiResult.Success(TokenPair(accessToken = "dummy-access-token-1", refreshToken = "dummy-refresh-token-1")),
            result,
        )
        val request = mock.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/login", request.path)
        assertEquals("""{"username":"app-test","password":"geheim"}""", request.body.readUtf8())
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `login mit falschen Zugangsdaten ist SessionExpired`() = runTest {
        mock.enqueue(jsonResponse(401, "auth/login_401.json"))

        assertEquals(ApiResult.Failure(ApiError.SessionExpired), mock.api.login("app-test", "falsch"))
    }

    @Test
    fun `login mit 422 ist ein unerwarteter HTTP-Fehler, keine falschen Zugangsdaten`() = runTest {
        // Ohne Bearer ist 422 kein JWT-Fehler; nur 401 heißt „falsch“ (Entscheidung #7).
        mock.enqueue(jsonResponse(422, "errors/jwt_invalid_422.json"))

        assertEquals(ApiResult.Failure(ApiError.Http(422)), mock.api.login("app-test", "geheim"))
    }

    @Test
    fun `login ohne refresh_token ist eine ungültige Antwort`() = runTest {
        mock.enqueue(MockResponse().setBody("""{"access_token":"nur-access"}"""))

        val result = mock.api.login("app-test", "geheim")

        assertTrue("war $result", (result as ApiResult.Failure).error is ApiError.InvalidResponse)
    }

    @Test
    fun `register schickt Zugangsdaten ohne Bearer und 201 ist Erfolg`() = runTest {
        mock.enqueue(jsonResponse(201, "auth/register_201.json"))

        val result = mock.api.register("neu", "geheim")

        assertEquals(ApiResult.Success(Unit), result)
        val request = mock.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/register", request.path)
        assertEquals("""{"username":"neu","password":"geheim"}""", request.body.readUtf8())
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `register mit vergebenem Benutzernamen ist HTTP 409`() = runTest {
        mock.enqueue(jsonResponse(409, "auth/register_409.json"))

        assertEquals(ApiResult.Failure(ApiError.Http(409)), mock.api.register("app-test", "geheim"))
    }

    @Test
    fun `register mit 401 ist ein HTTP-Fehler, kein Sitzungsende`() = runTest {
        mock.enqueue(MockResponse().setResponseCode(401))

        assertEquals(ApiResult.Failure(ApiError.Http(401)), mock.api.register("neu", "geheim"))
    }

    @Test
    fun `register ohne Server ist ein Netzfehler`() = runTest {
        mock.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = mock.api.register("neu", "geheim")

        assertTrue("war $result", (result as ApiResult.Failure).error is ApiError.Network)
    }

    @Test
    fun `refresh schickt das Refresh-Token als Bearer statt des Access-Tokens`() = runTest {
        mock.enqueue(jsonResponse(200, "auth/refresh_200.json"))

        val result = mock.api.refresh("dummy-refresh-token-1")

        assertEquals(ApiResult.Success(TokenPair("dummy-access-token-2", "dummy-refresh-token-2")), result)
        val request = mock.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/refresh", request.path)
        assertEquals("Bearer dummy-refresh-token-1", request.getHeader("Authorization"))
    }

    @Test
    fun `refresh mit ungültigem Refresh-Token ist Sitzungsende`() = runTest {
        mock.enqueue(jsonResponse(422, "auth/refresh_422_access_token.json"))

        assertEquals(ApiResult.Failure(ApiError.SessionExpired), mock.api.refresh("kaputt"))
    }

    @Test
    fun `geschützte Endpoints bekommen das Access-Token der Sitzung als Bearer`() = runTest {
        mock.accessToken = "aktuelles-token"
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        mock.api.suchprofile()

        assertEquals("Bearer aktuelles-token", mock.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `ohne Token geht der Request ohne Authorization-Header raus`() = runTest {
        mock.accessToken = null
        mock.enqueue(jsonResponse(401, "errors/jwt_missing_header_401.json"))

        mock.api.suchprofile()

        assertNull(mock.takeRequest().getHeader("Authorization"))
    }
}
