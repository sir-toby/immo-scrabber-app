package de.immoscrabber.app.login

import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.session.LoginResult
import de.immoscrabber.app.core.session.LogoutReason
import de.immoscrabber.app.core.session.SessionLogin
import de.immoscrabber.app.core.session.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private const val PROD = "https://prod.example.com/api/"

private data class LoginCall(val baseUrl: String, val username: String, val password: String)

/** Fake-[SessionLogin]: fester Zustand, zeichnet Anmeldungen auf und liefert [result]. */
private class FakeSessionLogin(initial: SessionState) : SessionLogin {
    override val state: StateFlow<SessionState> = MutableStateFlow(initial)
    val calls = mutableListOf<LoginCall>()
    var result = CompletableDeferred<LoginResult>(LoginResult.Success)

    override suspend fun login(baseUrl: String, username: String, password: String): LoginResult {
        calls += LoginCall(baseUrl, username, password)
        return result.await()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        session: FakeSessionLogin,
        allowLocalCleartext: Boolean = false,
    ) = LoginViewModel(session, PROD, allowLocalCleartext)

    private fun loggedOut(reason: LogoutReason? = null) =
        FakeSessionLogin(SessionState.LoggedOut(reason, baseUrl = "https://mein.example.com/api/", username = "anna"))

    private fun LoginViewModel.fill(username: String = "anna", password: String = "geheim", server: String? = null) {
        onUsernameChange(username)
        onPasswordChange(password)
        server?.let(::onServerUrlChange)
    }

    private fun failingWith(error: ApiError) = FakeSessionLogin(SessionState.LoggedOut(null, null, null)).apply {
        result = CompletableDeferred(LoginResult.Failure(error))
    }

    // --- Vorbelegung ---

    @Test
    fun `vorbelegt mit Server und Benutzername der letzten Sitzung`() {
        val state = viewModel(loggedOut()).state.value

        assertEquals("anna", state.username)
        assertEquals("https://mein.example.com/api/", state.serverUrl)
        assertEquals("", state.password)
        assertFalse(state.sessionExpired)
    }

    @Test
    fun `ohne frühere Sitzung steht die Prod-URL im Serverfeld`() {
        val state = viewModel(FakeSessionLogin(SessionState.LoggedOut(null, null, null))).state.value

        assertEquals("", state.username)
        assertEquals(PROD, state.serverUrl)
    }

    @Test
    fun `nach abgelaufener Sitzung steht der Hinweis, bis der Nutzer neu anmeldet`() {
        val session = loggedOut(LogoutReason.SessionExpired)
        val vm = viewModel(session)
        assertTrue(vm.state.value.sessionExpired)

        vm.fill()
        vm.submit()

        assertFalse(vm.state.value.sessionExpired)
    }

    // --- Absenden ---

    @Test
    fun `beim Absenden werden Server normalisiert und Benutzername getrimmt, das Passwort nicht`() = runTest(dispatcher) {
        val session = loggedOut()
        val vm = viewModel(session)

        vm.fill(username = "  anna ", password = " geheim ", server = " immo.example.com ")
        vm.submit()

        assertEquals(listOf(LoginCall("https://immo.example.com/api/", "anna", " geheim ")), session.calls)
        assertEquals("https://immo.example.com/api/", vm.state.value.serverUrl)
    }

    @Test
    fun `während der Anmeldung lädt es und ein zweites Absenden tut nichts`() = runTest(dispatcher) {
        val session = loggedOut().apply { result = CompletableDeferred() }
        val vm = viewModel(session)
        vm.fill()

        vm.submit()
        vm.submit()

        assertTrue(vm.state.value.loading)
        assertEquals(1, session.calls.size)
        session.result.complete(LoginResult.Success)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `ohne Benutzername oder Passwort lässt sich nicht absenden`() = runTest(dispatcher) {
        val session = loggedOut()
        val vm = viewModel(session)

        vm.fill(username = "  ", password = "geheim")
        vm.submit()
        vm.fill(username = "anna", password = "")
        vm.submit()

        assertEquals(emptyList<LoginCall>(), session.calls)
    }

    @Test
    fun `ungültiger Server klappt das Serverfeld auf, ohne anzumelden`() = runTest(dispatcher) {
        val session = loggedOut()
        val vm = viewModel(session)

        vm.fill(server = "ftp://immo.example.com")
        vm.submit()

        assertEquals(ServerError.Invalid, vm.state.value.serverError)
        assertTrue(vm.state.value.serverExpanded)
        assertEquals(emptyList<LoginCall>(), session.calls)
    }

    @Test
    fun `http wird im Release abgelehnt, im Debug für das lokale Backend erlaubt`() = runTest(dispatcher) {
        val release = viewModel(loggedOut()).apply { fill(server = "http://10.0.2.2:5000") }
        release.submit()
        assertEquals(ServerError.HttpsRequired, release.state.value.serverError)

        val session = loggedOut()
        val debug = viewModel(session, allowLocalCleartext = true).apply { fill(server = "http://10.0.2.2:5000") }
        debug.submit()
        assertNull(debug.state.value.serverError)
        assertEquals("http://10.0.2.2:5000/api/", session.calls.single().baseUrl)
    }

    // --- Fehlerzuordnung ---

    private fun errorAfterLogin(session: FakeSessionLogin): LoginError? {
        val vm = viewModel(session)
        vm.fill()
        vm.submit()
        return vm.state.value.error
    }

    @Test
    fun `401 heißt Benutzername oder Passwort falsch`() = runTest(dispatcher) {
        assertEquals(LoginError.WrongCredentials, errorAfterLogin(failingWith(ApiError.SessionExpired)))
    }

    @Test
    fun `422 ist ein unerwarteter Fehler mit HTTP-Code`() = runTest(dispatcher) {
        assertEquals(LoginError.Unexpected(httpCode = 422), errorAfterLogin(failingWith(ApiError.Http(422))))
    }

    @Test
    fun `Netzfehler heißt Server nicht erreichbar`() = runTest(dispatcher) {
        assertEquals(LoginError.ServerUnreachable, errorAfterLogin(failingWith(ApiError.Network(IOException()))))
    }

    @Test
    fun `Speicherfehler ist ein unerwarteter Fehler ohne Code`() = runTest(dispatcher) {
        val session = FakeSessionLogin(SessionState.LoggedOut(null, null, null)).apply {
            result = CompletableDeferred(LoginResult.StorageFailed)
        }
        assertEquals(LoginError.Unexpected(httpCode = null), errorAfterLogin(session))
    }

    @Test
    fun `eine Eingabe löscht den angezeigten Fehler`() = runTest(dispatcher) {
        val vm = viewModel(failingWith(ApiError.SessionExpired))
        vm.fill()
        vm.submit()
        assertEquals(LoginError.WrongCredentials, vm.state.value.error)

        vm.onPasswordChange("anders")

        assertNull(vm.state.value.error)
    }
}
