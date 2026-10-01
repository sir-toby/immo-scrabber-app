package de.immoscrabber.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.immoscrabber.app.core.AppContainer
import de.immoscrabber.app.core.session.SessionState
import de.immoscrabber.app.login.LoginScreen
import de.immoscrabber.app.login.LoginViewModel
import kotlinx.serialization.Serializable

/** Login als Vollbild ohne Bottom Navigation. */
@Serializable
data object LoginRoute

/** Hauptansicht mit Bottom Navigation ([MainShell]). */
@Serializable
data object MainRoute

/**
 * Wird erst gezeigt, wenn der Sitzungszustand feststeht (vorher hält der Splash). Folgt dem
 * [SessionState]: angemeldet → Hauptansicht, abgemeldet (Logout, Sitzungsende) → Login, jeweils
 * mit geleertem Back-Stack.
 */
@Composable
fun ImmoFinderNavHost(container: AppContainer, initialState: SessionState) {
    val navController = rememberNavController()
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()
    val startDestination: Any = if (initialState is SessionState.LoggedIn) MainRoute else LoginRoute

    NavHost(navController = navController, startDestination = startDestination) {
        composable<LoginRoute> {
            val viewModel: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LoginViewModel(container.sessionManager, container.prodBaseUrl, container.allowLocalCleartext)
                    }
                },
            )
            LoginScreen(viewModel)
        }
        composable<MainRoute> {
            // Beim Abmelden kann die Sitzung kurz vor dem Wechsel zum Login schon weg sein.
            val session = container.session ?: return@composable
            MainShell(session, container.benachrichtigungen, logout = container::logout)
        }
    }

    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.LoggedIn -> navController.navigateClearing(MainRoute)
            is SessionState.LoggedOut -> navController.navigateClearing(LoginRoute)
            SessionState.Loading -> Unit
        }
    }
}

private fun NavHostController.navigateClearing(route: Any) {
    if (currentDestination?.hasRoute(route::class) == true) return
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
