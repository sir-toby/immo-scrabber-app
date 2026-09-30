package de.immoscrabber.app.core.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource

/** Liest eine Fixture aus `src/test/resources/fixtures/`. */
fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResource("fixtures/$path")) {
        "Fixture fehlt: $path"
    }.readText()

fun jsonResponse(code: Int, fixturePath: String): MockResponse =
    MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(fixture(fixturePath))

/**
 * MockWebServer samt einem [ImmoApi], das über die echte [ApiClientFactory] gegen ihn gebaut
 * ist. Die Basis-URL endet wie in Prod auf `/api/`.
 */
class MockApiRule : ExternalResource() {
    val server = MockWebServer()
    var accessToken: String? = "test-access-token"
    lateinit var api: ImmoApi

    override fun before() {
        server.start()
        api = ApiClientFactory().create(
            baseUrl = server.url("/api/").toString(),
            tokenSource = AccessTokenSource { accessToken },
        )
    }

    override fun after() {
        server.shutdown()
    }

    fun enqueue(response: MockResponse) = server.enqueue(response)

    fun takeRequest() = server.takeRequest()
}
