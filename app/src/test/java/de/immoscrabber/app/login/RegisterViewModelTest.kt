package de.immoscrabber.app.login

import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.session.LoginResult
import de.immoscrabber.app.core.session.SessionRegistration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private const val SERVER = "https://immo.example.com/api/"

private data class Call(val what: String, val baseUrl: String, val username: String, val password: String)

/** Fake-[SessionRegistration]: zeichnet Registrierung und Anmeldung auf und liefert die Ergebnisse. */
private class FakeRegistration : SessionRegistration {
    val calls = mutableListOf<Call>()
    var registerResult = CompletableDeferred<ApiResult<Unit>>(ApiResult.Success(Unit))
    var loginResult: LoginResult = LoginResult.Success

    override suspend fun register(baseUrl: String, username: String, password: String): ApiResult<Unit> {
        calls += Call("register", baseUrl, username, password)
        return registerResult.await()
    }

    override suspend fun login(baseUrl: String, username: String, password: String): LoginResult {
        calls += Call("login", baseUrl, username, password)
        return loginResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val session = FakeRegistration()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Was an den Login weitergereicht wurde (geteilter Server-Bereich). */
    private val sharedServer = mutableListOf<String>()

    private fun viewModel(serverUrl: String = SERVER, allowLocalCleartext: Boolean = false) =
        RegisterViewModel(session, serverUrl, allowLocalCleartext, onServerUrlChange = { sharedServer += it })

    private fun RegisterViewModel.fill(
        username: String = "neu",
        password: String = "geheim",
        repeat: String = password,
        server: String? = null,
    ) {
        onUsernameChange(username)
        onPasswordChange(password)
        onPasswordRepeatChange(repeat)
        server?.let(::onServerUrlChange)
    }

    private fun failingWith(error: ApiError) {
        session.registerResult = CompletableDeferred(ApiResult.Failure(error))
    }

    private fun errorAfterSubmit(): RegisterError? {
        val vm = viewModel()
        vm.fill()
        vm.submit()
        return vm.state.value.error
    }

    // --- Anfangszustand und Validierung ---

    @Test
    fun `startet leer mit dem Server des Logins`() {
        val state = viewModel().state.value

        assertEquals(SERVER, state.serverUrl)
        assertEquals("", state.username)
        assertEquals("", state.password)
        assertEquals("", state.passwordRepeat)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `verschiedene Passwörter werden angezeigt und lassen sich nicht absenden`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(password = "geheim", repeat = "geheim2")

        vm.submit()

        assertTrue(vm.state.value.passwordMismatch)
        assertFalse(vm.state.value.canSubmit)
        assertEquals(emptyList<Call>(), session.calls)
    }

    @Test
    fun `solange die Wiederholung leer ist, gibt es keinen Hinweis auf verschiedene Passwörter`() {
        val vm = viewModel()
        vm.fill(repeat = "")

        assertFalse(vm.state.value.passwordMismatch)
        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun `ohne Benutzername oder Passwort lässt sich nicht absenden`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.fill(username = "  ")
        vm.submit()
        vm.fill(password = "")
        vm.submit()

        assertEquals(emptyList<Call>(), session.calls)
    }

    @Test
    fun `ungültiger Server klappt das Serverfeld auf, ohne zu registrieren`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(server = "ftp://immo.example.com")

        vm.submit()

        assertEquals(ServerError.Invalid, vm.state.value.serverError)
        assertTrue(vm.state.value.serverExpanded)
        assertEquals(emptyList<Call>(), session.calls)
    }

    @Test
    fun `http ist nur im Debug für das lokale Backend erlaubt`() = runTest(dispatcher) {
        val release = viewModel().apply { fill(server = "http://10.0.2.2:5000") }
        release.submit()
        assertEquals(ServerError.HttpsRequired, release.state.value.serverError)

        val debug = viewModel(allowLocalCleartext = true).apply { fill(server = "http://10.0.2.2:5000") }
        debug.submit()
        assertEquals("http://10.0.2.2:5000/api/", session.calls.first().baseUrl)
    }

    @Test
    fun `jede Server-Eingabe geht auch an den Login, damit sie Zurück übersteht`() {
        val vm = viewModel()

        vm.onServerUrlChange("andere")
        vm.onServerUrlChange("andere.example.com")

        assertEquals(listOf("andere", "andere.example.com"), sharedServer)
    }

    @Test
    fun `der normalisierte Server beim Absenden geht auch an den Login`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(server = " andere.example.com ")

        vm.submit()

        assertEquals("https://andere.example.com/api/", sharedServer.last())
    }

    // --- Registrieren und direkt anmelden ---

    @Test
    fun `registriert mit normalisiertem Server und getrimmtem Benutzernamen und meldet dann an`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill(username = "  neu ", password = " geheim ", server = " andere.example.com ")

        vm.submit()

        val expectedUrl = "https://andere.example.com/api/"
        assertEquals(
            listOf(
                Call("register", expectedUrl, "neu", " geheim "),
                Call("login", expectedUrl, "neu", " geheim "),
            ),
            session.calls,
        )
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.pleaseLogin)
    }

    @Test
    fun `nach erfolgreicher Anmeldung bleibt es beim Laden, bis die Navigation wechselt`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill()

        vm.submit()
        vm.submit()

        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.canSubmit)
        assertEquals(listOf("register", "login"), session.calls.map { it.what })
    }

    @Test
    fun `während der Registrierung lädt es und ein zweites Absenden tut nichts`() = runTest(dispatcher) {
        session.registerResult = CompletableDeferred()
        val vm = viewModel()
        vm.fill()

        vm.submit()
        vm.submit()

        assertTrue(vm.state.value.loading)
        assertEquals(1, session.calls.size)
        session.registerResult.complete(ApiResult.Success(Unit))
        assertEquals("login", session.calls.last().what)
    }

    @Test
    fun `scheitert die Anmeldung danach, geht es zum Login mit Benutzername und Server`() = runTest(dispatcher) {
        session.loginResult = LoginResult.Failure(ApiError.Network(IOException()))
        val vm = viewModel()
        vm.fill(username = " neu ")

        vm.submit()

        assertEquals(RegisteredUser(username = "neu", baseUrl = SERVER), vm.state.value.pleaseLogin)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `auch ein Speicherfehler nach der Registrierung führt zum Login`() = runTest(dispatcher) {
        session.loginResult = LoginResult.StorageFailed
        val vm = viewModel()
        vm.fill()

        vm.submit()

        assertEquals(RegisteredUser("neu", SERVER), vm.state.value.pleaseLogin)
    }

    // --- Fehlerzuordnung ---

    @Test
    fun `409 heißt Benutzername schon vergeben und es wird nicht angemeldet`() = runTest(dispatcher) {
        failingWith(ApiError.Http(409))

        assertEquals(RegisterError.UsernameTaken, errorAfterSubmit())
        assertEquals(listOf("register"), session.calls.map { it.what })
    }

    @Test
    fun `Netzfehler heißt Server nicht erreichbar`() = runTest(dispatcher) {
        failingWith(ApiError.Network(IOException()))

        assertEquals(RegisterError.ServerUnreachable, errorAfterSubmit())
    }

    @Test
    fun `andere Fehler sind unerwartet mit HTTP-Code`() = runTest(dispatcher) {
        failingWith(ApiError.Http(500))
        assertEquals(RegisterError.Unexpected(500), errorAfterSubmit())

        failingWith(ApiError.BadRequest("kaputt"))
        assertEquals(RegisterError.Unexpected(400), errorAfterSubmit())

        failingWith(ApiError.InvalidResponse(IllegalStateException()))
        assertEquals(RegisterError.Unexpected(null), errorAfterSubmit())
    }

    @Test
    fun `eine Eingabe löscht den angezeigten Fehler`() = runTest(dispatcher) {
        failingWith(ApiError.Http(409))
        val vm = viewModel()
        vm.fill()
        vm.submit()
        assertEquals(RegisterError.UsernameTaken, vm.state.value.error)

        vm.onUsernameChange("anders")

        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
