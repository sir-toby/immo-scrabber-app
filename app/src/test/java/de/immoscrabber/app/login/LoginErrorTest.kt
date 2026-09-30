package de.immoscrabber.app.login

import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.session.LoginResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class LoginErrorTest {
    private fun failure(error: ApiError) = LoginResult.Failure(error).toLoginError()

    @Test
    fun `401 heißt Benutzername oder Passwort falsch`() {
        assertEquals(LoginError.WrongCredentials, failure(ApiError.SessionExpired))
    }

    @Test
    fun `Netzfehler oder Timeout heißt Server nicht erreichbar`() {
        assertEquals(LoginError.ServerUnreachable, failure(ApiError.Network(IOException("timeout"))))
    }

    @Test
    fun `andere HTTP-Fehler zeigen den Code`() {
        assertEquals(LoginError.Unexpected(httpCode = 500), failure(ApiError.Http(500)))
        assertEquals(LoginError.Unexpected(httpCode = 400), failure(ApiError.BadRequest("kaputt")))
    }

    @Test
    fun `fehlendes refresh_token ist ein generischer Fehler ohne Code`() {
        assertEquals(LoginError.Unexpected(httpCode = null), failure(ApiError.InvalidResponse(RuntimeException())))
    }

    @Test
    fun `Speicherfehler ist ein generischer Fehler`() {
        assertEquals(LoginError.Unexpected(httpCode = null), LoginResult.StorageFailed.toLoginError())
    }

    @Test
    fun `Erfolg ist kein Fehler`() {
        assertNull(LoginResult.Success.toLoginError())
    }
}
