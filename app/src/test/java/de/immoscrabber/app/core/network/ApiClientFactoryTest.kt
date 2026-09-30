package de.immoscrabber.app.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.Authenticator
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientFactoryTest {
    private val server = MockWebServer().apply { start() }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `ein übergebener Authenticator darf nach 401 mit neuem Token wiederholen`() = runTest {
        var token = "altes-token"
        val authenticator = Authenticator { _, response ->
            token = "neues-token"
            response.request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        val api = ApiClientFactory().create(server.url("/api/").toString(), { token }, authenticator)
        server.enqueue(jsonResponse(401, "errors/jwt_expired_401.json"))
        server.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = api.suchprofile()

        assertTrue("war $result", result is ApiResult.Success)
        assertEquals("Bearer altes-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer neues-token", server.takeRequest().getHeader("Authorization"))
    }
}
