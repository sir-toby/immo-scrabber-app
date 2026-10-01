package de.immoscrabber.app.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.session.BaseUrlResult
import de.immoscrabber.app.core.session.LoginResult
import de.immoscrabber.app.core.session.SessionRegistration
import de.immoscrabber.app.core.session.normalizeBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Fehler des Registrieren-Screens (Texte in [RegisterScreen]). */
sealed interface RegisterError {
    data object UsernameTaken : RegisterError
    data object ServerUnreachable : RegisterError
    data class Unexpected(val httpCode: Int?) : RegisterError
}

/** Fehlertexte nach #12: 409 → vergeben, Netz/Timeout → nicht erreichbar, sonst unerwartet mit HTTP-Code. */
fun ApiError.toRegisterError(): RegisterError = when (this) {
    is ApiError.Http -> if (code == HTTP_CONFLICT) RegisterError.UsernameTaken else RegisterError.Unexpected(code)
    is ApiError.Network -> RegisterError.ServerUnreachable
    is ApiError.BadRequest -> RegisterError.Unexpected(httpCode = 400)
    ApiError.SessionExpired -> RegisterError.Unexpected(httpCode = 401)
    is ApiError.InvalidResponse -> RegisterError.Unexpected(httpCode = null)
}

private const val HTTP_CONFLICT = 409

/** Konto angelegt, aber nicht angemeldet: weiter zum Login mit diesen Werten. */
data class RegisteredUser(val username: String, val baseUrl: String)

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val passwordRepeat: String = "",
    val passwordVisible: Boolean = false,
    val serverUrl: String = "",
    val serverExpanded: Boolean = false,
    val serverError: ServerError? = null,
    val loading: Boolean = false,
    val error: RegisterError? = null,
    /** Gesetzt, wenn die direkte Anmeldung nach der Registrierung scheiterte. */
    val pleaseLogin: RegisteredUser? = null,
) {
    /** Erst ab der ersten Eingabe in „Passwort wiederholen“ anzeigen. */
    val passwordMismatch: Boolean get() = passwordRepeat.isNotEmpty() && password != passwordRepeat

    val canSubmit: Boolean
        get() = !loading && username.isNotBlank() && password.isNotEmpty() && password == passwordRepeat
}

/**
 * Registrieren (#12): lokal prüfen, `POST /auth/register`, bei Erfolg direkt anmelden.
 * Klappt die Anmeldung nicht, zeigt [RegisterUiState.pleaseLogin] den Weg zurück zum Login.
 * Nach erfolgreicher Anmeldung wechselt der SessionManager auf angemeldet, die Navigation folgt.
 *
 * @param initialServerUrl der Server aus dem Login-Screen (geteilt zwischen beiden Screens).
 * @param onServerUrlChange reicht jede Server-Eingabe an den Login weiter, damit sie Zurück übersteht.
 */
class RegisterViewModel(
    private val session: SessionRegistration,
    initialServerUrl: String,
    private val allowLocalCleartext: Boolean,
    private val onServerUrlChange: (String) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState(serverUrl = initialServerUrl))
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onPasswordRepeatChange(value: String) = _state.update { it.copy(passwordRepeat = value, error = null) }

    fun onTogglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onServerUrlChange(value: String) {
        _state.update { it.copy(serverUrl = value, serverError = null, error = null) }
        onServerUrlChange.invoke(value)
    }

    fun onToggleServerExpanded() = _state.update { it.copy(serverExpanded = !it.serverExpanded) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val baseUrl = when (val result = normalizeBaseUrl(current.serverUrl, allowLocalCleartext)) {
            is BaseUrlResult.Valid -> result.url
            BaseUrlResult.Invalid -> return showServerError(ServerError.Invalid)
            BaseUrlResult.HttpsRequired -> return showServerError(ServerError.HttpsRequired)
        }
        val username = current.username.trim()
        _state.update { it.copy(loading = true, error = null, serverUrl = baseUrl) }
        onServerUrlChange.invoke(baseUrl)
        viewModelScope.launch {
            when (val registered = session.register(baseUrl, username, current.password)) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = registered.error.toRegisterError()) }
                is ApiResult.Success -> {
                    // Nach erfolgreicher Anmeldung lädt es weiter, bis die Navigation wechselt;
                    // sonst ließe sich vorher noch einmal absenden (→ 409).
                    if (session.login(baseUrl, username, current.password) != LoginResult.Success) {
                        _state.update { it.copy(loading = false, pleaseLogin = RegisteredUser(username, baseUrl)) }
                    }
                }
            }
        }
    }

    private fun showServerError(error: ServerError) =
        _state.update { it.copy(serverError = error, serverExpanded = true) }
}
