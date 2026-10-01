package de.immoscrabber.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.push.Benachrichtigungen
import de.immoscrabber.app.core.session.Session
import de.immoscrabber.app.properties.PropertyTab
import de.immoscrabber.app.core.ui.icon
import de.immoscrabber.app.properties.pluralName
import de.immoscrabber.app.settings.KontoInfo
import de.immoscrabber.app.settings.SettingsTab
import de.immoscrabber.app.settings.SuchprofilEditor
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object HaeuserTab

@Serializable
data object WohnungenTab

@Serializable
data object GrundstueckeTab

@Serializable
data object EinstellungenTab

/**
 * Suchprofil-Editor als Vollbild ohne Bottom Navigation (Entscheidung #10). [profilId] `null` = neu;
 * [typ] ist dann der vorausgewählte Immobilientyp (`apiValue`) aus dem Tab „Noch kein Suchprofil“.
 * Er liegt im selben NavHost wie die Tabs, so führt Zurück/Speichern dorthin, woher er geöffnet wurde.
 */
@Serializable
data class SuchprofilEditorRoute(val profilId: String? = null, val typ: String? = null)

private enum class TopTab(val route: Any, @param:StringRes val title: Int, val icon: ImageVector) {
    Haeuser(HaeuserTab, PropertyType.HOUSE.pluralName, PropertyType.HOUSE.icon),
    Wohnungen(WohnungenTab, PropertyType.FLAT.pluralName, PropertyType.FLAT.icon),
    Grundstuecke(GrundstueckeTab, PropertyType.SITE.pluralName, PropertyType.SITE.icon),
    Einstellungen(EinstellungenTab, R.string.tab_settings, Icons.Outlined.Settings),
    ;

    val routeClass: KClass<*> get() = route::class
}

/**
 * Hauptansicht nach dem Login: Bottom Navigation Häuser · Wohnungen · Grundstücke ·
 * Einstellungen (Entscheidung #10). Jeder Tab behält beim Wechsel seinen Zustand (ViewModel,
 * Scrollposition); Zurück führt von jedem Tab erst zu „Häuser“, dann aus der App.
 */
@Composable
fun MainShell(session: Session, benachrichtigungen: Benachrichtigungen, logout: suspend () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val editorOffen = destination?.hasRoute(SuchprofilEditorRoute::class) == true
    // Snackbar nach Speichern/Löschen: Der Editor ist dann schon zu, also hier statt im Editor.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val editorSchliessen = {
        // Nur den Editor schließen, nie versehentlich einen Tab (etwa bei doppeltem Tippen).
        if (navController.currentDestination?.hasRoute(SuchprofilEditorRoute::class) == true) {
            navController.popBackStack()
        }
    }
    val editorFertig = { text: String ->
        editorSchliessen()
        scope.launch { snackbarHostState.showSnackbar(text) }
        Unit
    }
    val neuesSuchprofil = { type: PropertyType? -> navController.navigate(SuchprofilEditorRoute(typ = type?.apiValue)) }

    // Nach dem Login steht sonst die Tastatur des Passwortfelds noch über der Liste.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }

    // POST_NOTIFICATIONS einmal nach dem ersten erfolgreichen Login, nie wieder (Entscheidung #9).
    val berechtigungAnfragen = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (benachrichtigungen.sollAnfragen()) {
            benachrichtigungen.alsGefragtMerken()
            berechtigungAnfragen.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!editorOffen) NavigationBar {
                TopTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.title)) },
                    )
                }
            }
        },
        // Die Tabs haben eigene Scaffolds; hier nur die Höhe der Bottom Navigation abziehen.
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HaeuserTab,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable<HaeuserTab> {
                PropertyTab(PropertyType.HOUSE, session.inserate, session.veraltet) { neuesSuchprofil(PropertyType.HOUSE) }
            }
            composable<WohnungenTab> {
                PropertyTab(PropertyType.FLAT, session.inserate, session.veraltet) { neuesSuchprofil(PropertyType.FLAT) }
            }
            composable<GrundstueckeTab> {
                PropertyTab(PropertyType.SITE, session.inserate, session.veraltet) { neuesSuchprofil(PropertyType.SITE) }
            }
            composable<EinstellungenTab> {
                SettingsTab(
                    repository = session.suchprofile,
                    konto = KontoInfo(session.username, session.baseUrl, BuildConfig.VERSION_NAME),
                    logout = logout,
                    benachrichtigungenErlaubt = benachrichtigungen::erlaubt,
                    benachrichtigungenEinstellungen = benachrichtigungen::systemEinstellungenIntent,
                    onNeuesSuchprofil = { neuesSuchprofil(null) },
                    onSuchprofilOeffnen = { navController.navigate(SuchprofilEditorRoute(profilId = it.id)) },
                )
            }
            composable<SuchprofilEditorRoute> { entry ->
                val route = entry.toRoute<SuchprofilEditorRoute>()
                SuchprofilEditor(
                    repository = session.suchprofile,
                    profilId = route.profilId,
                    vorauswahl = PropertyType.fromApiValue(route.typ),
                    onClose = editorSchliessen,
                    onFertig = editorFertig,
                )
            }
        }
    }
}
