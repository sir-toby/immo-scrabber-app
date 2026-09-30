package de.immoscrabber.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme
import kotlinx.serialization.Serializable

/** Typsichere Route des vorläufigen Start-Screens; die Slices ersetzen ihn durch Login/Tabs. */
@Serializable
data object StartRoute

@Composable
fun ImmoFinderNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = StartRoute) {
        composable<StartRoute> { StartScreen() }
    }
}

@Composable
private fun StartScreen() {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.start_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun StartScreenPreview() {
    ImmoFinderTheme { StartScreen() }
}
