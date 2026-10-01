package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.network.MockApiRule
import de.immoscrabber.app.core.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class GeraeteRegistrierungTest {
    @get:Rule val mock = MockApiRule()

    private var fcmToken: String? = "fcm-token-1"
    private var angemeldet = true
    private val loggedIn = SessionState.LoggedIn("https://immo.example.com/api/", "app-test")
    private val loggedOut = SessionState.LoggedOut(null, null, null)
    private val state = MutableStateFlow<SessionState>(loggedIn)
    private val registrierung = GeraeteRegistrierung(
        sessionState = state,
        fcmToken = { fcmToken ?: throw IllegalStateException("keine Play-Dienste") },
        api = { if (angemeldet) mock.api else null },
        abmeldeTimeoutMillis = 500,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun nextRequest() = mock.server.takeRequest(2, TimeUnit.SECONDS)
    private fun noRequest() = assertNull(mock.server.takeRequest(300, TimeUnit.MILLISECONDS))

    @Test
    fun `bei jedem Wechsel auf angemeldet wird das Token per PUT registriert`() = runBlocking {
        state.value = SessionState.Loading
        repeat(2) { mock.enqueue(MockResponse().setResponseCode(204)) }
        scope.launch { registrierung.folgeSitzung() }

        noRequest()
        state.value = loggedIn // App-Start mit gespeicherter Sitzung bzw. Login
        val first = nextRequest()!!
        assertEquals("PUT", first.method)
        assertEquals("/api/devices", first.path)
        assertEquals("""{"token":"fcm-token-1"}""", first.body.readUtf8())

        state.value = loggedOut
        noRequest()
        state.value = loggedIn.copy(username = "anderer") // erneuter Login
        assertEquals("PUT", nextRequest()?.method)
    }

    @Test
    fun `ein neues Token wird mit Sitzung sofort registriert`() = runBlocking {
        mock.enqueue(MockResponse().setResponseCode(204))

        registrierung.neuesToken("fcm-token-2")

        assertEquals("""{"token":"fcm-token-2"}""", nextRequest()?.body?.readUtf8())
    }

    @Test
    fun `ein neues Token im kalten Prozess wartet, bis die Sitzung gelesen ist`() = runBlocking {
        state.value = SessionState.Loading
        angemeldet = false // Session noch nicht geöffnet
        mock.enqueue(MockResponse().setResponseCode(204))

        scope.launch { registrierung.neuesToken("fcm-token-2") }
        noRequest()
        angemeldet = true
        state.value = loggedIn

        assertEquals("""{"token":"fcm-token-2"}""", nextRequest()?.body?.readUtf8())
    }

    @Test
    fun `ein unerwarteter Fehler beendet das Folgen der Sitzung nicht`() = runBlocking {
        state.value = SessionState.Loading
        var erstesMal = true
        val wackelig = GeraeteRegistrierung(
            sessionState = state,
            fcmToken = { "fcm-token-1" },
            api = {
                if (erstesMal) {
                    erstesMal = false
                    throw IllegalStateException("unerwartet")
                }
                mock.api
            },
        )
        mock.enqueue(MockResponse().setResponseCode(204))
        scope.launch { wackelig.folgeSitzung() }

        state.value = loggedIn
        noRequest()
        state.value = loggedOut
        state.value = loggedIn.copy(username = "anderer")

        assertEquals("PUT", nextRequest()?.method)
    }

    @Test
    fun `ein neues Token ohne Sitzung wird nicht geschickt`() = runBlocking {
        state.value = loggedOut
        angemeldet = false

        registrierung.neuesToken("fcm-token-2")

        noRequest()
    }

    @Test
    fun `ohne FCM-Token passiert nichts und nichts stürzt ab`() = runBlocking {
        fcmToken = null

        registrierung.registrieren()
        registrierung.abmelden()

        noRequest()
    }

    @Test
    fun `scheitert die Registrierung, bleibt das folgenlos`() = runBlocking {
        mock.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        registrierung.registrieren()

        assertEquals(1, mock.server.requestCount)
    }

    @Test
    fun `beim Logout wird das Token per DELETE abgemeldet`() = runBlocking {
        mock.enqueue(MockResponse().setResponseCode(204))

        registrierung.abmelden()

        val request = nextRequest()!!
        assertEquals("DELETE", request.method)
        assertEquals("/api/devices/fcm-token-1", request.path)
    }

    @Test
    fun `das Abmelden wartet höchstens kurz auf den Server`() = runBlocking {
        mock.enqueue(MockResponse().setResponseCode(204).setHeadersDelay(5, TimeUnit.SECONDS))

        val start = System.nanoTime()
        registrierung.abmelden()

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000)
    }
}
