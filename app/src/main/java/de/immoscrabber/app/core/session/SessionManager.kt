package de.immoscrabber.app.core.session

import android.os.SystemClock
import de.immoscrabber.app.BuildConfig
import de.immoscrabber.app.core.data.ApiInseratRepository
import de.immoscrabber.app.core.data.ApiSuchprofilRepository
import de.immoscrabber.app.core.data.InseratRepository
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.data.VeraltetMerker
import de.immoscrabber.app.core.network.ApiClientFactory
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.ImmoApi
import de.immoscrabber.app.core.network.TokenPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Anmeldezustand der App. */
sealed interface SessionState {
    /** Speicher wird noch gelesen (Splash bleibt stehen). */
    data object Loading : SessionState

    /** Abgemeldet; [baseUrl] und [username] der letzten Sitzung zum Vorbelegen des Logins. */
    data class LoggedOut(
        val reason: LogoutReason?,
        val baseUrl: String?,
        val username: String?,
    ) : SessionState

    data class LoggedIn(val baseUrl: String, val username: String) : SessionState
}

enum class LogoutReason {
    /** Refresh abgelehnt oder Tokens unlesbar: „Sitzung abgelaufen, bitte neu anmelden“. */
    SessionExpired,
}

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val error: ApiError) : LoginResult

    /** Anmeldung am Server ok, aber Tokens ließen sich nicht speichern. */
    data object StorageFailed : LoginResult
}

/**
 * Was der Login-Screen vom [SessionManager] braucht: den Zustand zum Vorbelegen und das Anmelden.
 * In Tests ersetzt ein Fake den SessionManager.
 */
interface SessionLogin {
    val state: StateFlow<SessionState>

    /** Meldet mit bereits normalisierter [baseUrl] an und speichert Tokens, Server und Username. */
    suspend fun login(baseUrl: String, username: String, password: String): LoginResult
}

/**
 * Alles, was zu genau einer Sitzung gehört. Wird bei Logout/Sitzungsende komplett
 * verworfen (Entscheidung #10); spätere Repositories samt Caches hängen hier an.
 */
class Session internal constructor(
    val baseUrl: String,
    val username: String,
    val api: ImmoApi,
    internal val tokens: SessionTokens,
) {
    /** Inserate dieser Sitzung; ihre späteren Caches verschwinden mit der Sitzung. */
    val inserate: InseratRepository by lazy { ApiInseratRepository(api) }

    /** Suchprofile dieser Sitzung, im Speicher bis zum Logout/Sitzungsende. */
    val suchprofile: SuchprofilRepository by lazy { ApiSuchprofilRepository(api) }

    /**
     * „Veraltet“-Merker je Immobilientyp (Entscheidung #10). Der Suchprofil-Editor (#32) ruft nach
     * jedem Speichern/Löschen `veraltet.markStale(type)`. Die Schwelle ist 30 Minuten; nur im
     * Debug-Build lässt sie sich mit `-Pimmo.staleAfterSeconds=<n>` verkürzen.
     */
    val veraltet: VeraltetMerker by lazy {
        VeraltetMerker(
            now = { SystemClock.elapsedRealtime().milliseconds },
            threshold = BuildConfig.STALE_AFTER_SECONDS.seconds,
        )
    }

    internal fun close() {
        tokens.close()
    }
}

/**
 * Anmelden, angemeldet bleiben, Sitzungsende (Entscheidung #7).
 *
 * @param scope App-weiter Scope für den Refresh beim Start (fire-and-forget).
 */
class SessionManager(
    private val tokenStore: TokenStore,
    private val prefsStore: SessionPrefsStore,
    private val apiClientFactory: ApiClientFactory,
    private val scope: CoroutineScope,
) : SessionLogin {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Die laufende Sitzung, `null` wenn abgemeldet. */
    @Volatile
    var currentSession: Session? = null
        private set

    /**
     * Liest den Speicher: mit Tokens sofort angemeldet (ohne aufs Netz zu warten) und
     * einmal Refresh im Hintergrund; sonst abgemeldet. Unlesbare Tokens = Sitzungsende.
     */
    suspend fun start() {
        val session = mutex.withLock {
            if (_state.value != SessionState.Loading) return
            val prefs = prefsStore.read()
            when (val stored = tokenStore.read()) {
                StoredTokens.None -> loggedOut(null, prefs.baseUrl, prefs.username)
                StoredTokens.Unreadable -> {
                    clearTokens()
                    loggedOut(LogoutReason.SessionExpired, prefs.baseUrl, prefs.username)
                }
                is StoredTokens.Present -> {
                    if (prefs.baseUrl != null && prefs.username != null) {
                        return@withLock open(prefs.baseUrl, prefs.username, stored.tokens)
                    }
                    // Tokens ohne Server: unbrauchbar, sauber abmelden.
                    clearTokens()
                    loggedOut(null, prefs.baseUrl, prefs.username)
                }
            }
            null
        } ?: return
        scope.launch(Dispatchers.IO) { session.tokens.refresh(session.tokens.accessToken()) }
    }

    override suspend fun login(baseUrl: String, username: String, password: String): LoginResult {
        val loginApi = apiClientFactory.create(baseUrl, tokenSource = { null })
        val tokens = when (val result = loginApi.login(username, password)) {
            is ApiResult.Failure -> return LoginResult.Failure(result.error)
            is ApiResult.Success -> result.value
        }
        return mutex.withLock {
            val saved = succeeds {
                tokenStore.save(tokens)
                prefsStore.save(SessionPrefs(baseUrl, username))
            }
            if (!saved) return@withLock LoginResult.StorageFailed
            currentSession?.close()
            open(baseUrl, username, tokens)
            LoginResult.Success
        }
    }

    /** Lokaler Logout: Tokens und Sitzung weg, Server und Username bleiben. */
    suspend fun logout() = mutex.withLock {
        val session = currentSession ?: return@withLock
        discard(session)
        loggedOut(null, session.baseUrl, session.username)
    }

    private suspend fun endSession(session: Session) = mutex.withLock {
        if (currentSession !== session) return@withLock
        discard(session)
        loggedOut(LogoutReason.SessionExpired, session.baseUrl, session.username)
    }

    private suspend fun persistIfCurrent(session: Session, tokens: TokenPair): Boolean = mutex.withLock {
        if (currentSession !== session) return@withLock false
        // Scheitert das Speichern, gilt das neue Paar trotzdem im Speicher; beim nächsten Start hilft der alte Refresh.
        succeeds { tokenStore.save(tokens) }
        true
    }

    private fun open(baseUrl: String, username: String, tokens: TokenPair): Session {
        // Refresh über einen Client ohne Refresh-Interceptor, sonst ruft ein 401 ihn erneut auf.
        val refreshApi = apiClientFactory.create(baseUrl, tokenSource = { null })
        lateinit var session: Session
        val sessionTokens = SessionTokens(
            initial = tokens,
            callRefresh = refreshApi::refresh,
            persist = { persistIfCurrent(session, it) },
            onRefreshRejected = { endSession(session) },
        )
        session = Session(
            baseUrl = baseUrl,
            username = username,
            api = apiClientFactory.create(baseUrl, sessionTokens, TokenRefreshInterceptor(sessionTokens)),
            tokens = sessionTokens,
        )
        currentSession = session
        _state.value = SessionState.LoggedIn(baseUrl, username)
        return session
    }

    private suspend fun discard(session: Session) {
        currentSession = null
        session.close()
        clearTokens()
    }

    private suspend fun clearTokens() {
        // Scheitert das, ist nichts zu retten; die Sitzung im Speicher ist ohnehin verworfen.
        succeeds { tokenStore.clear() }
    }

    private fun loggedOut(reason: LogoutReason?, baseUrl: String?, username: String?) {
        _state.value = SessionState.LoggedOut(reason, baseUrl, username)
    }
}

/**
 * Führt [block] aus und meldet, ob er ohne Ausnahme durchlief. Für Speicherzugriffe, deren
 * Scheitern die Sitzung nicht aufhalten darf; der Abbruch der Coroutine wird durchgereicht.
 */
private inline fun succeeds(block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        false
    }
