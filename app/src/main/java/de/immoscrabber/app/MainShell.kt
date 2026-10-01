package de.immoscrabber.app

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.session.Session
import de.immoscrabber.app.properties.PropertyTab
import de.immoscrabber.app.core.ui.icon
import de.immoscrabber.app.properties.pluralName
import de.immoscrabber.app.settings.KontoInfo
import de.immoscrabber.app.settings.SettingsTab
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
fun MainShell(session: Session, logout: suspend () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // Nach dem Login steht sonst die Tastatur des Passwortfelds noch über der Liste.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            composable<HaeuserTab> { PropertyTab(PropertyType.HOUSE, session.inserate, session.veraltet) }
            composable<WohnungenTab> { PropertyTab(PropertyType.FLAT, session.inserate, session.veraltet) }
            composable<GrundstueckeTab> { PropertyTab(PropertyType.SITE, session.inserate, session.veraltet) }
            composable<EinstellungenTab> {
                SettingsTab(
                    repository = session.suchprofile,
                    konto = KontoInfo(session.username, session.baseUrl, BuildConfig.VERSION_NAME),
                    logout = logout,
                    // TODO(#32): Suchprofil-Editor als Vollbild-Route öffnen (neu bzw. mit diesem Profil).
                    onNeuesSuchprofil = {},
                    onSuchprofilOeffnen = {},
                )
            }
        }
    }
}
