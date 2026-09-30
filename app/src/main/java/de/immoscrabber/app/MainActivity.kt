package de.immoscrabber.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Das Login-Ticket hält den Splash per setKeepOnScreenCondition, bis DataStore gelesen ist.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ImmoFinderTheme {
                ImmoFinderNavHost()
            }
        }
    }
}
