package de.immoscrabber.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.immoscrabber.app.R
import de.immoscrabber.app.core.data.MAX_SUCHPROFILE
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.ui.icon

/** Wer angemeldet ist und welche App-Version läuft; kommt aus der Sitzung bzw. `BuildConfig`. */
data class KontoInfo(val username: String, val baseUrl: String, val appVersion: String)

/**
 * Einstieg des Tabs: ViewModel am Back-Stack-Eintrag des Tabs, Suchprofile aus dem Repository der
 * Sitzung, [logout] meldet lokal ab.
 */
@Composable
fun SettingsTab(
    repository: SuchprofilRepository,
    konto: KontoInfo,
    logout: suspend () -> Unit,
    onNeuesSuchprofil: () -> Unit,
    onSuchprofilOeffnen: (Suchprofil) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(repository, logout) } },
    )
    SettingsScreen(viewModel, konto, onNeuesSuchprofil, onSuchprofilOeffnen)
}

/**
 * Einstellungen-Tab (Entscheidung #8): eine scrollende Seite mit den Sektionen Suchprofile (n/10),
 * Benachrichtigungen, Konto und der App-Version ganz unten.
 *
 * @param onNeuesSuchprofil „+ Neues Suchprofil“; öffnet den Editor (#32).
 * @param onSuchprofilOeffnen Tipp auf ein Profil; öffnet es im Editor (#32).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    konto: KontoInfo,
    onNeuesSuchprofil: () -> Unit,
    onSuchprofilOeffnen: (Suchprofil) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    val refreshFailedText = stringResource(R.string.settings_profiles_load_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.RefreshFailed -> snackbarHostState.showSnackbar(refreshFailedText)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                suchprofilSektion(state.suchprofile, viewModel::retry, onNeuesSuchprofil, onSuchprofilOeffnen)
                benachrichtigungenSektion()
                kontoSektion(konto, onLogout = { confirmLogout = true })
                item(key = "version") {
                    Text(
                        text = stringResource(R.string.settings_app_version, konto.appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        LogoutDialog(
            onConfirm = {
                confirmLogout = false
                viewModel.abmelden()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

private fun LazyListScope.sektionsKopf(key: String, title: @Composable () -> String) {
    item(key = key) {
        Text(
            text = title(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

private fun LazyListScope.suchprofilSektion(
    state: SuchprofileState,
    onRetry: () -> Unit,
    onNeuesSuchprofil: () -> Unit,
    onSuchprofilOeffnen: (Suchprofil) -> Unit,
) {
    sektionsKopf("profile-header") {
        if (state is SuchprofileState.Geladen) {
            stringResource(R.string.settings_profiles_header, state.profile.size, MAX_SUCHPROFILE)
        } else {
            stringResource(R.string.settings_profiles_header_loading)
        }
    }
    when (state) {
        SuchprofileState.Laden -> item(key = "profile-loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        SuchprofileState.Fehler -> item(key = "profile-error") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_profiles_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        is SuchprofileState.Geladen -> {
            items(state.profile, key = { "profile-${it.id}" }) { profil ->
                SuchprofilZeile(profil, onClick = { onSuchprofilOeffnen(profil) })
            }
            item(key = "profile-new") {
                if (state.maximumErreicht) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_profiles_max, MAX_SUCHPROFILE)) },
                        leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_profiles_new)) },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.primary,
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.clickable(onClick = onNeuesSuchprofil),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuchprofilZeile(profil: Suchprofil, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(formatSuchprofilTitel(profil)) },
        supportingContent = { Text(formatLimits(profil)) },
        leadingContent = {
            Icon(profil.propertyType?.icon ?: Icons.Outlined.Search, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Platzhalter der Sektion „Benachrichtigungen“. Das Push-Ticket (Entscheidung #9) ersetzt diese
 * Zeile durch die echten Einstellungen; die Sektion sitzt schon an ihrer Stelle.
 */
private fun LazyListScope.benachrichtigungenSektion() {
    item(key = "notifications-divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
    sektionsKopf("notifications-header") { stringResource(R.string.settings_notifications_header) }
    item(key = "notifications-placeholder") {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications_placeholder)) },
            leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            colors = ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

private fun LazyListScope.kontoSektion(konto: KontoInfo, onLogout: () -> Unit) {
    item(key = "account-divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
    sektionsKopf("account-header") { stringResource(R.string.settings_account_header) }
    item(key = "account-user") {
        Text(
            text = stringResource(R.string.settings_account_logged_in, konto.username, formatHost(konto.baseUrl)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    item(key = "account-logout") {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_logout)) },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            colors = ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.clickable(onClick = onLogout),
        )
    }
}

/** Bestätigung vor dem Logout (Entscheidung #7); er wirkt nur lokal. */
@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_logout_title)) },
        text = { Text(stringResource(R.string.settings_logout_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_logout)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
