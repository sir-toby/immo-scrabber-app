package de.immoscrabber.app.core

import android.content.Context
import de.immoscrabber.app.BuildConfig
import de.immoscrabber.app.core.network.ApiClientFactory

/**
 * Handverdrahtete Abhängigkeiten der App (kein Hilt/Koin, Entscheidung #10).
 *
 * Wächst mit den Slices: Sitzungsspeicher, später ein Sitzungs-Container (OkHttp/Retrofit,
 * Repositories samt Caches), der bei Logout/Sitzungsende komplett verworfen wird.
 */
class AppContainer(
    @Suppress("unused") private val applicationContext: Context,
) {
    /** Voreingestellter Server (Prod) aus der Gradle-Property `immo.prodBaseUrl`. */
    val prodBaseUrl: String = BuildConfig.PROD_BASE_URL

    /**
     * Baut das [de.immoscrabber.app.core.network.ImmoApi] einer Sitzung (Basis-URL,
     * Token-Quelle, später der Refresh-Authenticator aus #26).
     */
    val apiClientFactory: ApiClientFactory = ApiClientFactory()
}
