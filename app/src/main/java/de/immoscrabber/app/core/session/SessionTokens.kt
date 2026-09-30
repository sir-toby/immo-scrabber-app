package de.immoscrabber.app.core.session

import de.immoscrabber.app.core.network.AUTHORIZATION
import de.immoscrabber.app.core.network.AccessTokenSource
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.TokenPair
import de.immoscrabber.app.core.network.bearer
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Das Token-Paar einer Sitzung im Speicher, samt Single-Flight-Refresh (Entscheidung #7).
 *
 * [refresh] blockiert und wird vom OkHttp-Thread (reaktiv) wie vom Start-Refresh gerufen;
 * ein Lock sorgt dafür, dass parallele 401/422 genau einen `/auth/refresh` auslösen.
 */
internal class SessionTokens(
    initial: TokenPair,
    /** `/auth/refresh` über einen Client OHNE [TokenRefreshInterceptor]. */
    private val callRefresh: suspend (refreshToken: String) -> ApiResult<TokenPair>,
    /** Speichert das neue Paar; `false`, wenn die Sitzung inzwischen verworfen ist. */
    private val persist: suspend (TokenPair) -> Boolean,
    /** `/auth/refresh` hat 401/422 geliefert: Sitzungsende. */
    private val onRefreshRejected: suspend () -> Unit,
) : AccessTokenSource {

    @Volatile private var tokens: TokenPair? = initial
    private val lock = Any()

    override fun accessToken(): String? = tokens?.accessToken

    /** Sitzung verworfen: ab jetzt kein Token mehr, kein Refresh. Nimmt bewusst kein Lock. */
    fun close() {
        tokens = null
    }

    /**
     * Frischt auf, wenn [usedAccessToken] noch das aktuelle Token ist; hat ein anderer
     * Request inzwischen aufgefrischt, kommt direkt das neue Token zurück.
     *
     * @return das Access-Token für die Wiederholung, oder `null` (kein Retry).
     */
    fun refresh(usedAccessToken: String?): String? = synchronized(lock) {
        val current = tokens ?: return null
        if (usedAccessToken != current.accessToken) return current.accessToken
        when (val result = runBlocking { callRefresh(current.refreshToken) }) {
            is ApiResult.Success -> {
                if (tokens == null || !runBlocking { persist(result.value) }) return null
                tokens = result.value
                result.value.accessToken
            }
            is ApiResult.Failure -> {
                // Netzfehler und 5xx beenden die Sitzung nie, nur 401/422 von /auth/refresh.
                if (result.error == ApiError.SessionExpired) {
                    tokens = null
                    runBlocking { onRefreshRejected() }
                }
                null
            }
        }
    }
}

/**
 * Reaktiver Refresh: Kommt auf einen Request mit Bearer-Token 401 oder 422 zurück, wird
 * (Single-Flight) aufgefrischt und genau einmal mit dem neuen Token wiederholt.
 *
 * Ein Interceptor statt eines OkHttp-`Authenticator`, weil der `Authenticator` nur bei 401
 * greift, flask_jwt_extended für ungültige Tokens aber 422 liefert. Liegt hinter dem
 * Bearer-Interceptor und sieht daher das tatsächlich gesendete Token.
 */
internal class TokenRefreshInterceptor(private val tokens: SessionTokens) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 401 && response.code != 422) return response
        // Ohne Bearer (z. B. /auth/login) heißt 401 etwas anderes: durchreichen.
        val sent = request.header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX) ?: return response
        val retryToken = tokens.refresh(sent) ?: return response
        response.close()
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, bearer(retryToken)).build())
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
