package de.immoscrabber.app.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.immoscrabber.app.core.session.BaseUrlResult
import de.immoscrabber.app.core.session.LogoutReason
import de.immoscrabber.app.core.session.SessionLogin
import de.immoscrabber.app.core.session.SessionState
import de.immoscrabber.app.core.session.normalizeBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Problem mit der Server-Eingabe. */
enum class ServerError { Invalid, HttpsRequired }

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val serverUrl: String = "",
    val serverExpanded: Boolean = false,
    val serverError: ServerError? = null,
    val loading: Boolean = false,
    val error: LoginError? = null,
    /** „Sitzung abgelaufen, bitte neu anmelden“, bis der Nutzer es neu versucht. */
    val sessionExpired: Boolean = false,
) {
    val canSubmit: Boolean get() = !loading && username.isNotBlank() && password.isNotEmpty()
}

/**
 * Login-Screen: vorbelegt mit Server und Username der letzten Sitzung (sonst Prod-URL).
 * Nach erfolgreichem Login wechselt der SessionManager auf [SessionState.LoggedIn],
 * die Navigation reagiert darauf.
 */
class LoginViewModel(
    private val session: SessionLogin,
    prodBaseUrl: String,
    private val allowLocalCleartext: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(session.state.value, prodBaseUrl))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onTogglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, serverError = null, error = null) }

    fun onToggleServerExpanded() = _state.update { it.copy(serverExpanded = !it.serverExpanded) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val baseUrl = when (val result = normalizeBaseUrl(current.serverUrl, allowLocalCleartext)) {
            is BaseUrlResult.Valid -> result.url
            BaseUrlResult.Invalid -> return showServerError(ServerError.Invalid)
            BaseUrlResult.HttpsRequired -> return showServerError(ServerError.HttpsRequired)
        }
        _state.update { it.copy(loading = true, error = null, sessionExpired = false, serverUrl = baseUrl) }
        viewModelScope.launch {
            val error = session.login(baseUrl, current.username.trim(), current.password).toLoginError()
            _state.update { it.copy(loading = false, error = error) }
        }
    }

    private fun showServerError(error: ServerError) =
        _state.update { it.copy(serverError = error, serverExpanded = true) }

    private companion object {
        fun initialState(session: SessionState, prodBaseUrl: String): LoginUiState {
            val loggedOut = session as? SessionState.LoggedOut
            return LoginUiState(
                username = loggedOut?.username.orEmpty(),
                serverUrl = loggedOut?.baseUrl ?: prodBaseUrl,
                sessionExpired = loggedOut?.reason == LogoutReason.SessionExpired,
            )
        }
    }
}
