package de.immoscrabber.app.core.session

import de.immoscrabber.app.core.network.ApiClientFactory
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.TokenPair
import de.immoscrabber.app.core.network.fixture
import de.immoscrabber.app.core.network.jsonResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SessionManagerTest {
    private val server = MockWebServer().apply { start() }
    private val baseUrl = server.url("/api/").toString()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tokenStore = FakeTokenStore()
    private val prefsStore = FakeSessionPrefsStore()
    private val manager = SessionManager(tokenStore, prefsStore, ApiClientFactory(), scope)

    private val oldTokens = TokenPair("access-alt", "refresh-alt")
    private val newTokens = TokenPair("dummy-access-token-2", "dummy-refresh-token-2")

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    /** Gespeicherte Sitzung wie nach einem früheren Login. */
    private fun seedSession() {
        tokenStore.stored = StoredTokens.Present(oldTokens)
        prefsStore.prefs = SessionPrefs(baseUrl, "app-test")
    }

    /**
     * Start mit gespeicherter Sitzung; der Hintergrund-Refresh beim Start bekommt eine 503
     * und ändert damit nichts.
     */
    private suspend fun startLoggedIn() {
        seedSession()
        server.enqueue(MockResponse().setResponseCode(503))
        manager.start()
        assertEquals("/api/auth/refresh", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    private fun api() = requireNotNull(manager.currentSession) { "keine Sitzung" }.api

    // --- Start ---

    @Test
    fun `ohne Tokens startet die App abgemeldet mit vorbelegtem Server und Benutzernamen`() = runBlocking {
        prefsStore.prefs = SessionPrefs(baseUrl, "app-test")

        manager.start()

        assertEquals(SessionState.LoggedOut(reason = null, baseUrl = baseUrl, username = "app-test"), manager.state.value)
        assertNull(manager.currentSession)
    }

    @Test
    fun `vor dem Start ist der Zustand Loading`() {
        assertEquals(SessionState.Loading, manager.state.value)
    }

    @Test
    fun `mit Tokens ist man sofort angemeldet und frischt einmal im Hintergrund auf`() = runBlocking {
        seedSession()
        server.enqueue(jsonResponse(200, "auth/refresh_200.json"))

        manager.start()

        assertEquals(SessionState.LoggedIn(baseUrl, "app-test"), manager.state.value)
        val refresh = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/auth/refresh", refresh.path)
        assertEquals("Bearer refresh-alt", refresh.getHeader("Authorization"))
        eventually { assertEquals(StoredTokens.Present(newTokens), tokenStore.stored) }
    }

    @Test
    fun `lehnt der Server den Start-Refresh mit 401 ab, endet die Sitzung`() = runBlocking {
        seedSession()
        server.enqueue(jsonResponse(401, "errors/jwt_expired_401.json"))

        manager.start()

        eventually {
            assertEquals(SessionState.LoggedOut(LogoutReason.SessionExpired, baseUrl, "app-test"), manager.state.value)
        }
        assertEquals(StoredTokens.None, tokenStore.stored)
    }

    @Test
    fun `ist der Server beim Start nicht erreichbar, bleibt man angemeldet`() = runBlocking {
        seedSession()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        manager.start()
        server.takeRequest(5, TimeUnit.SECONDS)
        delay(200)

        assertEquals(SessionState.LoggedIn(baseUrl, "app-test"), manager.state.value)
        assertEquals(StoredTokens.Present(oldTokens), tokenStore.stored)
    }

    @Test
    fun `unlesbare Tokens beenden die Sitzung`() = runBlocking {
        tokenStore.stored = StoredTokens.Unreadable
        prefsStore.prefs = SessionPrefs(baseUrl, "app-test")

        manager.start()

        assertEquals(SessionState.LoggedOut(LogoutReason.SessionExpired, baseUrl, "app-test"), manager.state.value)
        assertEquals(StoredTokens.None, tokenStore.stored)
    }

    // --- Login ---

    @Test
    fun `Login speichert Tokens, Server und Benutzernamen und meldet an`() = runBlocking {
        manager.start()
        server.enqueue(jsonResponse(200, "auth/login_200.json"))

        val result = manager.login(baseUrl, "app-test", "app-test-password")

        assertEquals(LoginResult.Success, result)
        assertEquals(SessionState.LoggedIn(baseUrl, "app-test"), manager.state.value)
        assertEquals(StoredTokens.Present(TokenPair("dummy-access-token-1", "dummy-refresh-token-1")), tokenStore.stored)
        assertEquals(SessionPrefs(baseUrl, "app-test"), prefsStore.prefs)
        assertEquals("/api/auth/login", server.takeRequest().path)
    }

    @Test
    fun `nach dem Login gehen Requests mit dem neuen Access-Token raus`() = runBlocking {
        manager.start()
        server.enqueue(jsonResponse(200, "auth/login_200.json"))
        manager.login(baseUrl, "app-test", "app-test-password")
        server.takeRequest()
        server.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        api().suchprofile()

        assertEquals("Bearer dummy-access-token-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `falsches Passwort meldet nicht an und speichert nichts`() = runBlocking {
        manager.start()
        server.enqueue(jsonResponse(401, "auth/login_401.json"))

        val result = manager.login(baseUrl, "app-test", "falsch")

        assertEquals(LoginResult.Failure(ApiError.SessionExpired), result)
        assertEquals(SessionState.LoggedOut(null, null, null), manager.state.value)
        assertEquals(StoredTokens.None, tokenStore.stored)
        assertEquals(SessionPrefs(null, null), prefsStore.prefs)
        assertNull(manager.currentSession)
    }

    // --- Registrierung ---

    @Test
    fun `Registrieren ruft den angegebenen Server und ändert weder Zustand noch Speicher`() = runBlocking {
        manager.start()
        server.enqueue(jsonResponse(201, "auth/register_201.json"))

        val result = manager.register(baseUrl, "neu", "geheim")

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals("/api/auth/register", server.takeRequest().path)
        assertEquals(SessionState.LoggedOut(null, null, null), manager.state.value)
        assertEquals(SessionPrefs(null, null), prefsStore.prefs)
        assertNull(manager.currentSession)
    }

    // --- Reaktiver Refresh ---

    @Test
    fun `401 auf geschütztem Endpoint frischt auf und wiederholt mit neuem Token`() = runBlocking {
        startLoggedIn()
        server.dispatcher = TokenServer(validAccessToken = newTokens.accessToken)

        val result = api().suchprofile()

        assertTrue("war $result", result is ApiResult.Success)
        assertEquals(StoredTokens.Present(newTokens), tokenStore.stored)
    }

    @Test
    fun `422 wird wie 401 behandelt`() = runBlocking {
        startLoggedIn()
        server.dispatcher = TokenServer(validAccessToken = newTokens.accessToken, rejectCode = 422)

        val result = api().suchprofile()

        assertTrue("war $result", result is ApiResult.Success)
        assertEquals(StoredTokens.Present(newTokens), tokenStore.stored)
    }

    @Test
    fun `parallele 401 lösen genau einen Refresh aus`() = runBlocking {
        startLoggedIn()
        val tokenServer = TokenServer(validAccessToken = newTokens.accessToken, refreshDelayMs = 300)
        server.dispatcher = tokenServer

        val results = (1..8).map { async(Dispatchers.IO) { api().suchprofile() } }.awaitAll()

        assertTrue("war $results", results.all { it is ApiResult.Success })
        assertEquals(1, tokenServer.refreshCount.get())
    }

    @Test
    fun `lehnt auch der Refresh ab, gibt es genau einen Versuch und die Sitzung endet`() = runBlocking {
        startLoggedIn()
        val tokenServer = TokenServer(validAccessToken = newTokens.accessToken, refreshCode = 401)
        server.dispatcher = tokenServer

        val result = api().suchprofile()

        assertEquals(ApiResult.Failure(ApiError.SessionExpired), result)
        assertEquals(1, tokenServer.refreshCount.get())
        assertEquals(SessionState.LoggedOut(LogoutReason.SessionExpired, baseUrl, "app-test"), manager.state.value)
        assertEquals(StoredTokens.None, tokenStore.stored)
        assertEquals(SessionPrefs(baseUrl, "app-test"), prefsStore.prefs)
        assertNull(manager.currentSession)
    }

    @Test
    fun `5xx beim Refresh beendet die Sitzung nicht`() = runBlocking {
        startLoggedIn()
        server.dispatcher = TokenServer(validAccessToken = newTokens.accessToken, refreshCode = 503)

        val result = api().suchprofile()

        assertEquals(ApiResult.Failure(ApiError.SessionExpired), result)
        assertEquals(SessionState.LoggedIn(baseUrl, "app-test"), manager.state.value)
        assertEquals(StoredTokens.Present(oldTokens), tokenStore.stored)
    }

    @Test
    fun `wird das neue Token auch abgelehnt, wird nicht endlos wiederholt`() = runBlocking {
        startLoggedIn()
        val tokenServer = TokenServer(validAccessToken = "gibt-es-nicht")
        server.dispatcher = tokenServer

        val result = api().suchprofile()

        assertEquals(ApiResult.Failure(ApiError.SessionExpired), result)
        assertEquals(1, tokenServer.refreshCount.get())
        assertEquals(2, tokenServer.protectedCount.get())
        assertEquals(SessionState.LoggedIn(baseUrl, "app-test"), manager.state.value)
    }

    // --- Logout ---

    @Test
    fun `Logout löscht Tokens und Sitzung, behält Server und Benutzernamen`() = runBlocking {
        startLoggedIn()

        manager.logout()

        assertEquals(SessionState.LoggedOut(null, baseUrl, "app-test"), manager.state.value)
        assertEquals(StoredTokens.None, tokenStore.stored)
        assertEquals(SessionPrefs(baseUrl, "app-test"), prefsStore.prefs)
        assertNull(manager.currentSession)
    }

    @Test
    fun `nach dem Logout gibt der alte Client kein Token mehr heraus`() = runBlocking {
        startLoggedIn()
        val oldApi = api()

        manager.logout()
        server.enqueue(jsonResponse(401, "errors/jwt_missing_header_401.json"))
        oldApi.suchprofile()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    // --- Hilfen ---

    private suspend fun eventually(assertion: () -> Unit) {
        withTimeout(5_000) {
            while (true) {
                try {
                    assertion()
                    return@withTimeout
                } catch (_: AssertionError) {
                    delay(20)
                }
            }
        }
    }

    /**
     * Backend-Attrappe: geschützte Endpoints akzeptieren nur [validAccessToken], sonst
     * [rejectCode]; `/auth/refresh` antwortet nach [refreshDelayMs] mit [refreshCode].
     */
    private class TokenServer(
        private val validAccessToken: String,
        private val rejectCode: Int = 401,
        private val refreshCode: Int = 200,
        private val refreshDelayMs: Long = 0,
    ) : Dispatcher() {
        val refreshCount = AtomicInteger()
        val protectedCount = AtomicInteger()

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path == "/api/auth/refresh") {
                refreshCount.incrementAndGet()
                Thread.sleep(refreshDelayMs)
                return when (refreshCode) {
                    200 -> jsonResponse(200, "auth/refresh_200.json")
                    401 -> jsonResponse(401, "errors/jwt_expired_401.json")
                    else -> MockResponse().setResponseCode(refreshCode).setBody(fixture("errors/internal_server_error_500.html"))
                }
            }
            protectedCount.incrementAndGet()
            return if (request.getHeader("Authorization") == "Bearer $validAccessToken") {
                jsonResponse(200, "preferences/preferences_list.json")
            } else {
                jsonResponse(rejectCode, if (rejectCode == 422) "errors/jwt_invalid_422.json" else "errors/jwt_expired_401.json")
            }
        }
    }
}

private class FakeTokenStore : TokenStore {
    @Volatile var stored: StoredTokens = StoredTokens.None

    override suspend fun read() = stored
    override suspend fun save(tokens: TokenPair) {
        stored = StoredTokens.Present(tokens)
    }
    override suspend fun clear() {
        stored = StoredTokens.None
    }
}

private class FakeSessionPrefsStore : SessionPrefsStore {
    @Volatile var prefs = SessionPrefs(null, null)

    override suspend fun read() = prefs
    override suspend fun save(prefs: SessionPrefs) {
        this.prefs = prefs
    }
}
