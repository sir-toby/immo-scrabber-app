package de.immoscrabber.app.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * #56: Ein einmaliger Fehler beim Bild-Download ließ dauerhaft den Platzhalter stehen.
 * Der Bild-Client wiederholt nur Verbindungsfehler und puffert den Body nie (große Fotos).
 */
class ImageHttpClientTest {
    private val server = MockWebServer().apply { start() }

    @After fun tearDown() = server.shutdown()

    private fun request() = Request.Builder().url(server.url("/bild.jpg")).build()

    private fun load(client: OkHttpClient = createImageHttpClient()): String =
        client.newCall(request()).execute().use { it.body!!.string() }

    private fun connectionDrop() = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

    @Test
    fun `ein Verbindungsfehler beim Bild-Download wird einmal still wiederholt`() {
        server.enqueue(connectionDrop())
        server.enqueue(MockResponse().setBody("bilddaten"))

        assertEquals("bilddaten", load())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `ein Bild-Download wird höchstens einmal wiederholt`() {
        server.enqueue(connectionDrop())
        server.enqueue(connectionDrop())
        server.enqueue(MockResponse().setBody("bilddaten"))

        val error = runCatching { load() }.exceptionOrNull()

        assertTrue("war $error", error is IOException)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `der Bild-Client puffert den Body nicht, ein Abbruch beim Lesen wird nicht wiederholt`() {
        server.enqueue(
            MockResponse().setBody("bilddaten").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        server.enqueue(MockResponse().setBody("bilddaten"))

        val error = runCatching { load() }.exceptionOrNull()

        assertTrue("war $error", error is IOException)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `ein abgebrochener Bild-Call wird nicht wiederholt`() {
        assertCancelledCallNotRetried(createImageHttpClient())
    }

    @Test
    fun `ein abgebrochener API-Call wird nicht wiederholt`() {
        assertCancelledCallNotRetried(
            OkHttpClient.Builder().addInterceptor(RetryIdempotentReadInterceptor()).build(),
        )
    }

    /** Der Server antwortet erst nach 2 s; der Call wird vorher abgebrochen. */
    private fun assertCancelledCallNotRetried(client: OkHttpClient) {
        server.enqueue(MockResponse().setBody("bilddaten").setHeadersDelay(2, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody("bilddaten"))
        val call = client.newCall(request())
        val canceller = Executors.newSingleThreadScheduledExecutor()
        canceller.schedule({ call.cancel() }, 300, TimeUnit.MILLISECONDS)

        val error = runCatching { call.execute().use { it.body!!.string() } }.exceptionOrNull()
        canceller.shutdown()

        assertTrue("war $error", error is IOException)
        assertEquals(1, server.requestCount)
    }
}
