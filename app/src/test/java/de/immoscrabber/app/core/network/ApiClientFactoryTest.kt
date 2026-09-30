package de.immoscrabber.app.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientFactoryTest {
    private val server = MockWebServer().apply { start() }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `ein übergebener Refresh-Interceptor sieht das gesendete Token und darf wiederholen`() = runTest {
        var token = "altes-token"
        var seenByInterceptor: String? = null
        val refreshInterceptor = Interceptor { chain ->
            seenByInterceptor = chain.request().header("Authorization")
            val response = chain.proceed(chain.request())
            if (response.code != 401) return@Interceptor response
            response.close()
            token = "neues-token"
            chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
        }
        val api = ApiClientFactory().create(server.url("/api/").toString(), { token }, refreshInterceptor)
        server.enqueue(jsonResponse(401, "errors/jwt_expired_401.json"))
        server.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = api.suchprofile()

        assertTrue("war $result", result is ApiResult.Success)
        assertEquals("Bearer altes-token", seenByInterceptor)
        assertEquals("Bearer altes-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer neues-token", server.takeRequest().getHeader("Authorization"))
    }
}
