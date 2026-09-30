package de.immoscrabber.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.immoscrabber.app.core.session.SessionState
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as ImmoFinderApp).container
        val sessionState = container.sessionManager.state
        // Splash bleibt, bis der Sitzungsspeicher gelesen ist (Entscheidung #7).
        installSplashScreen().setKeepOnScreenCondition { sessionState.value == SessionState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ImmoFinderTheme {
                val state by sessionState.collectAsStateWithLifecycle()
                if (state != SessionState.Loading) {
                    // Startziel nur einmal festlegen; danach navigiert der NavHost selbst.
                    val initialState = remember { state }
                    ImmoFinderNavHost(container, initialState)
                }
            }
        }
    }
}
