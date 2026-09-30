package de.immoscrabber.app.login

import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.session.LoginResult

/** Fehler des Login-Screens (Texte in [LoginScreen]). */
sealed interface LoginError {
    data object WrongCredentials : LoginError
    data object ServerUnreachable : LoginError
    data class Unexpected(val httpCode: Int?) : LoginError
}

/**
 * Fehlertexte nach Entscheidung #7: 401 → falsche Zugangsdaten, Netz/Timeout → Server nicht
 * erreichbar, sonst unerwarteter Fehler mit HTTP-Code; fehlendes `refresh_token` ist generisch.
 */
fun LoginResult.toLoginError(): LoginError? = when (this) {
    LoginResult.Success -> null
    LoginResult.StorageFailed -> LoginError.Unexpected(httpCode = null)
    is LoginResult.Failure -> when (val e = error) {
        ApiError.SessionExpired -> LoginError.WrongCredentials
        is ApiError.Network -> LoginError.ServerUnreachable
        is ApiError.BadRequest -> LoginError.Unexpected(httpCode = 400)
        is ApiError.Http -> LoginError.Unexpected(httpCode = e.code)
        is ApiError.InvalidResponse -> LoginError.Unexpected(httpCode = null)
    }
}
