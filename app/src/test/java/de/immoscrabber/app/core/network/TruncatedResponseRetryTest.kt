package de.immoscrabber.app.core.network

import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression #43: Beim Kaltstart kam der Body einer 200-Antwort abgeschnitten an
 * („unexpected end of stream“), und der Tab zeigte „Konnte Häuser nicht laden“.
 */
class TruncatedResponseRetryTest {
    @get:Rule val mock = MockApiRule()

    private fun truncated(): MockResponse =
        jsonResponse(200, PAGE).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

    @Test
    fun `ein abgeschnittener GET-Body wird einmal still wiederholt`() = runTest {
        mock.enqueue(truncated())
        mock.enqueue(jsonResponse(200, PAGE))

        val result = mock.api.inserate(PropertyType.HOUSE, Label.UNBEWERTET)

        assertTrue("war $result", result is ApiResult.Success)
        assertEquals(2, mock.server.requestCount)
    }

    @Test
    fun `ein GET wird höchstens einmal wiederholt`() = runTest {
        mock.enqueue(truncated())
        mock.enqueue(truncated())
        mock.enqueue(jsonResponse(200, PAGE))

        val result = mock.api.inserate(PropertyType.HOUSE, Label.UNBEWERTET)

        assertTrue("war $result", (result as? ApiResult.Failure)?.error is ApiError.Network)
        assertEquals(2, mock.server.requestCount)
    }

    @Test
    fun `ein PATCH wird nicht still wiederholt`() = runTest {
        mock.enqueue(truncated())
        mock.enqueue(jsonResponse(200, PAGE))

        val result = mock.api.bewerten(PropertyType.HOUSE, "id-1", Label.INTERESSANT)

        assertTrue("war $result", (result as? ApiResult.Failure)?.error is ApiError.Network)
        assertEquals(1, mock.server.requestCount)
    }

    private companion object {
        const val PAGE = "properties/results_houses_page1.json"
    }
}
