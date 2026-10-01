package de.immoscrabber.app.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.immoscrabber.app.R
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme

/**
 * „Registrieren“ (#12) im Design des Logins. Nach erfolgreicher Anmeldung übernimmt die
 * Navigation (Sitzungszustand); scheitert sie, geht es über [onPleaseLogin] zurück zum Login.
 *
 * @param onBack zurück zum Login, die Eingaben gehen verloren.
 */
@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onPleaseLogin: (RegisteredUser) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.pleaseLogin) { state.pleaseLogin?.let(onPleaseLogin) }
    RegisterContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordRepeatChange = viewModel::onPasswordRepeatChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onServerUrlChange = viewModel::onServerUrlChange,
        onToggleServerExpanded = viewModel::onToggleServerExpanded,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordRepeatChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onToggleServerExpanded: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }
    AuthScaffold(title = stringResource(R.string.register_title)) {
        Spacer(Modifier.height(16.dp))
        UsernameField(state.username, onUsernameChange, enabled = !state.loading)
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.login_password),
            visible = state.passwordVisible,
            onToggleVisible = onTogglePasswordVisible,
            enabled = !state.loading,
            contentType = ContentType.NewPassword,
        )
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = state.passwordRepeat,
            onValueChange = onPasswordRepeatChange,
            label = stringResource(R.string.register_password_repeat),
            visible = state.passwordVisible,
            onToggleVisible = onTogglePasswordVisible,
            enabled = !state.loading,
            contentType = ContentType.NewPassword,
            onDone = submit,
            errorText = if (state.passwordMismatch) stringResource(R.string.register_password_mismatch) else null,
        )
        state.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            AuthMessage(errorText(error))
        }
        Spacer(Modifier.height(20.dp))
        BrandButton(
            text = stringResource(R.string.register_submit),
            loading = state.loading,
            enabled = state.canSubmit,
            onClick = submit,
        )
        AuthSwitchLink(
            question = stringResource(R.string.register_have_account),
            action = stringResource(R.string.login_submit),
            enabled = !state.loading,
            onClick = onBack,
        )
        ServerSection(
            serverUrl = state.serverUrl,
            expanded = state.serverExpanded,
            error = state.serverError,
            enabled = !state.loading,
            onServerUrlChange = onServerUrlChange,
            onToggleExpanded = onToggleServerExpanded,
            onDone = submit,
        )
    }
}

@Composable
private fun errorText(error: RegisterError): String = when (error) {
    RegisterError.UsernameTaken -> stringResource(R.string.register_error_username_taken)
    RegisterError.ServerUnreachable -> stringResource(R.string.login_error_unreachable)
    is RegisterError.Unexpected -> error.httpCode
        ?.let { stringResource(R.string.login_error_unexpected_http, it) }
        ?: stringResource(R.string.login_error_unexpected)
}

@Preview(showSystemUi = true)
@Composable
private fun RegisterPreview() {
    ImmoFinderTheme {
        RegisterContent(
            state = RegisterUiState(
                username = "neu",
                password = "geheim",
                passwordRepeat = "geheim2",
                serverUrl = "https://immo.example.com/api/",
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onPasswordRepeatChange = {},
            onTogglePasswordVisible = {},
            onServerUrlChange = {},
            onToggleServerExpanded = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
