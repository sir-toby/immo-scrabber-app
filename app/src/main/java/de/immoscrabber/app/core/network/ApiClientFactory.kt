package de.immoscrabber.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Invocation
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Liefert das aktuelle Access-Token der Sitzung (oder `null` ohne Sitzung).
 *
 * Implementiert von der Sitzung (`core.session.SessionTokens`). Wird auf dem
 * OkHttp-Thread gerufen, muss also schnell und threadsicher sein.
 */
fun interface AccessTokenSource {
    fun accessToken(): String?
}

/**
 * Baut OkHttp + Retrofit für die Basis-URL einer Sitzung und liefert das [ImmoApi].
 *
 * - [AccessTokenSource]: woher der Bearer-Interceptor das Token nimmt.
 * - `refreshInterceptor`: reaktiver Refresh bei 401/422 (`core.session.TokenRefreshInterceptor`).
 *   Liegt hinter dem Bearer-Interceptor, sieht also das gesendete Token. Ohne ihn (Login,
 *   Refresh selbst) kommt ein 401/422 direkt als [ApiError.SessionExpired] an.
 */
class ApiClientFactory {

    fun create(
        baseUrl: String,
        tokenSource: AccessTokenSource,
        refreshInterceptor: Interceptor? = null,
    ): ImmoApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Suchprofile speichern geocodiert synchron beim Server und kann dauern.
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(BearerTokenInterceptor(tokenSource))
            .apply { if (refreshInterceptor != null) addInterceptor(refreshInterceptor) }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return ImmoApi(retrofit.create(ImmoApiService::class.java))
    }
}

/**
 * JSON-Konfiguration für das Backend: unbekannte Felder ignorieren. Fehlende Felder decken
 * die `= null`-Defaults der DTOs ab; `null` in Request-DTOs ohne Default wird mitgeschickt.
 */
internal val ApiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * Hängt `Authorization: Bearer <token>` an jeden Request, außer an Endpoints mit
 * [Unauthenticated] und an Requests, die schon einen `Authorization`-Header tragen
 * (`/auth/refresh` schickt das Refresh-Token selbst).
 */
internal class BearerTokenInterceptor(private val tokenSource: AccessTokenSource) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val unauthenticated = request.tag(Invocation::class.java)
            ?.method()?.isAnnotationPresent(Unauthenticated::class.java) == true
        if (unauthenticated || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val token = tokenSource.accessToken() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, bearer(token)).build())
    }
}

internal const val AUTHORIZATION = "Authorization"

internal fun bearer(token: String) = "Bearer $token"
