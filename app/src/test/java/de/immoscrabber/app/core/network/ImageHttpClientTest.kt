package de.immoscrabber.app.core.network

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** #56: Ein einmaliger Fehler beim Bild-Download ließ dauerhaft den Platzhalter stehen. */
class ImageHttpClientTest {
    private val server = MockWebServer().apply { start() }

    @After fun tearDown() = server.shutdown()

    private fun truncated() = MockResponse().setBody("bilddaten")
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

    private fun load(): String {
        val request = Request.Builder().url(server.url("/bild.jpg")).build()
        return createImageHttpClient().newCall(request).execute().use { it.body!!.string() }
    }

    @Test
    fun `ein abgebrochener Bild-Download wird einmal still wiederholt`() {
        server.enqueue(truncated())
        server.enqueue(MockResponse().setBody("bilddaten"))

        assertEquals("bilddaten", load())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `ein Bild-Download wird höchstens einmal wiederholt`() {
        server.enqueue(truncated())
        server.enqueue(truncated())
        server.enqueue(MockResponse().setBody("bilddaten"))

        val error = runCatching { load() }.exceptionOrNull()

        assertEquals(true, error is IOException)
        assertEquals(2, server.requestCount)
    }
}
