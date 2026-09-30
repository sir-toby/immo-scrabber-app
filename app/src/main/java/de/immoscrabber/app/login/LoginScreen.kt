package de.immoscrabber.app.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.immoscrabber.app.R
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onServerUrlChange = viewModel::onServerUrlChange,
        onToggleServerExpanded = viewModel::onToggleServerExpanded,
        onSubmit = viewModel::submit,
    )
}

/**
 * Login nach Web-Vorbild (Entscheidung #20): Vollbild-Foto, darüber eine abgerundete Karte.
 * Die Karte scrollt mit der Tastatur, das Bild bleibt stehen; im Dark Mode dunkle Karte
 * und ein Schleier über dem Bild.
 */
@Composable
private fun LoginContent(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onToggleServerExpanded: () -> Unit,
    onSubmit: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isSystemInDarkTheme()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginCard(
                state = state,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onTogglePasswordVisible = onTogglePasswordVisible,
                onServerUrlChange = onServerUrlChange,
                onToggleServerExpanded = onToggleServerExpanded,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun LoginCard(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onServerUrlChange: (String) -> Unit,
    onToggleServerExpanded: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Card(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Brand()
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.sessionExpired) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_session_expired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                enabled = !state.loading,
                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                }),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible) {
                        Icon(
                            painter = painterResource(
                                if (state.passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            ),
                            contentDescription = stringResource(
                                if (state.passwordVisible) R.string.login_password_hide else R.string.login_password_show,
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
            )
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorText(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                enabled = state.canSubmit,
                // Exakte Seed-Farbe wie im Web-Login (das Theme-Primary ist heller).
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.brand_blue),
                    contentColor = Color.White,
                    disabledContainerColor = colorResource(R.color.brand_blue).copy(alpha = 0.45f),
                    disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }
            Spacer(Modifier.height(12.dp))
            ServerSection(
                state = state,
                onServerUrlChange = onServerUrlChange,
                onToggleExpanded = onToggleServerExpanded,
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.icon_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.login_brand),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Eingeklappter Bereich „Server“; zeigt eingeklappt den Host der aktuellen Adresse. */
@Composable
private fun ServerSection(
    state: LoginUiState,
    onServerUrlChange: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onDone: () -> Unit,
) {
    val rotation by animateFloatAsState(if (state.serverExpanded) 180f else 0f, label = "chevron")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.login_server),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.serverExpanded) {
                    Text(
                        text = state.serverUrl.displayHost(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = state.serverExpanded) {
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(R.string.login_server_url)) },
                singleLine = true,
                enabled = !state.loading,
                isError = state.serverError != null,
                supportingText = {
                    Text(
                        when (state.serverError) {
                            ServerError.Invalid -> stringResource(R.string.login_server_invalid)
                            ServerError.HttpsRequired -> stringResource(R.string.login_server_https_required)
                            null -> stringResource(R.string.login_server_hint)
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun errorText(error: LoginError): String = when (error) {
    LoginError.WrongCredentials -> stringResource(R.string.login_error_wrong_credentials)
    LoginError.ServerUnreachable -> stringResource(R.string.login_error_unreachable)
    is LoginError.Unexpected -> error.httpCode
        ?.let { stringResource(R.string.login_error_unexpected_http, it) }
        ?: stringResource(R.string.login_error_unexpected)
}

private fun String.displayHost(): String {
    val url = trim().let { if ("://" in it) it else "https://$it" }.toHttpUrlOrNull() ?: return trim()
    return if (url.port == 443 || url.port == 80) url.host else "${url.host}:${url.port}"
}

@Preview(showSystemUi = true)
@Composable
private fun LoginPreview() {
    ImmoFinderTheme {
        LoginContent(
            state = LoginUiState(username = "app-test", serverUrl = "https://immo.example.com/api/", sessionExpired = true),
            onUsernameChange = {},
            onPasswordChange = {},
            onTogglePasswordVisible = {},
            onServerUrlChange = {},
            onToggleServerExpanded = {},
            onSubmit = {},
        )
    }
}
