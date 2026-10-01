package de.immoscrabber.app.core

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.SingletonImageLoader
import de.immoscrabber.app.BuildConfig
import de.immoscrabber.app.core.network.ApiClientFactory
import de.immoscrabber.app.core.network.createImageHttpClient
import de.immoscrabber.app.core.session.DataStoreSessionPrefsStore
import de.immoscrabber.app.core.session.EncryptedTokenStore
import de.immoscrabber.app.core.session.Session
import de.immoscrabber.app.core.session.SessionCrypto
import de.immoscrabber.app.core.session.SessionManager
import de.immoscrabber.app.core.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Unverschlüsselter Speicher für Server und Username (Datei `datastore/session.preferences_pb`). */
private val Context.sessionPrefsDataStore by preferencesDataStore(name = "session")

/** Verschlüsselte Token-Datei; muss zu den Backup-Regeln passen. */
private const val TOKEN_FILE = "session_tokens.pb"

/**
 * Handverdrahtete Abhängigkeiten der App (kein Hilt/Koin, Entscheidung #10).
 *
 * App-weit leben hier nur Speicher und [sessionManager]. Alles Sitzungsbezogene (API-Client,
 * später Repositories samt Caches) hängt an [session] und wird bei Logout/Sitzungsende
 * komplett verworfen.
 */
class AppContainer(applicationContext: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Voreingestellter Server (Prod) aus `IMMO_PROD_BASE_URL` bzw. `local.properties`; leer, wenn nicht gesetzt. */
    val prodBaseUrl: String = BuildConfig.PROD_BASE_URL

    /** Debug-Build: `http://` für das lokale Backend (10.0.2.2, localhost) erlaubt. */
    val allowLocalCleartext: Boolean = BuildConfig.DEBUG

    val apiClientFactory: ApiClientFactory = ApiClientFactory()

    /** OkHttp-Client für Anbieterbilder (Coil), sitzungsunabhängig; Bilder cacht Coil auf der Platte. */
    val imageHttpClient: OkHttpClient by lazy { createImageHttpClient() }

    private val aead by lazy { SessionCrypto.keystoreAead(applicationContext) }

    val sessionManager = SessionManager(
        tokenStore = EncryptedTokenStore.create(
            file = applicationContext.dataStoreFile(TOKEN_FILE),
            aead = { aead },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ),
        prefsStore = DataStoreSessionPrefsStore(applicationContext.sessionPrefsDataStore),
        apiClientFactory = apiClientFactory,
        scope = appScope,
    )

    /** Die laufende Sitzung (API-Client usw.), `null` wenn abgemeldet. */
    val session: Session? get() = sessionManager.currentSession

    init {
        appScope.launch(Dispatchers.IO) { sessionManager.start() }
        appScope.launch { clearImageCachesWhenSessionEnds(applicationContext) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(BackgroundTimer())
    }

    /**
     * Meldet dem Veraltet-Merker der laufenden Sitzung, wann die App in den Hintergrund geht und
     * zurückkommt (> 30 min → alle Listen veraltet, Entscheidung #10). Drehen zählt nicht, das
     * deckt `ProcessLifecycleOwner` ab; ein Custom Tab schon, denn er läuft in einer anderen App.
     */
    private inner class BackgroundTimer : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            session?.veraltet?.onBackground()
        }

        override fun onStart(owner: LifecycleOwner) {
            session?.veraltet?.onForeground()
        }
    }

    /**
     * Logout und Sitzungsende leeren auch Coils Bildcache (Speicher und Platte), damit der nächste
     * Nutzer keine fremden Inserate sieht (Entscheidung #10, Nachtrag in #31). Der Cache ist
     * app-weit und hängt nicht an der [Session], deshalb hier am Übergang angemeldet → abgemeldet.
     */
    private suspend fun clearImageCachesWhenSessionEnds(context: Context) {
        var previous: SessionState = SessionState.Loading
        sessionManager.state.collect { state ->
            if (previous is SessionState.LoggedIn && state is SessionState.LoggedOut) {
                val imageLoader = SingletonImageLoader.get(context)
                imageLoader.memoryCache?.clear()
                withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
            }
            previous = state
        }
    }
}
