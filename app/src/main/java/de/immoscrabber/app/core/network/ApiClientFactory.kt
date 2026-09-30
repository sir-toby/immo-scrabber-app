package de.immoscrabber.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Invocation
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
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
            // Zuerst: eine Wiederholung durchläuft Bearer und Refresh erneut, mit aktuellem Token.
            .addInterceptor(RetryIdempotentReadInterceptor())
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

/**
 * Wiederholt ein `GET` genau einmal, wenn Senden oder Lesen mit einer [IOException] scheitert,
 * etwa bei einem abgeschnittenen Body („unexpected end of stream“, #43). Dafür liest er den
 * Body hier vollständig; OkHttps eigene Wiederholung greift nur beim Verbindungsaufbau.
 *
 * Nur `GET`: Andere Methoden ändern Daten und werden nie still wiederholt (Entscheidung #6).
 * Ein abgebrochener Call wird nicht wiederholt.
 *
 * [bufferBody] `false` (Bild-Client, #56): Der Body bleibt ein Stream, damit große Fotos nicht
 * ganz im Speicher landen. Wiederholt wird dann nur, wenn schon `proceed()` scheitert
 * (Verbindung, Header), nicht ein Abbruch beim späteren Lesen des Bodys.
 */
internal class RetryIdempotentReadInterceptor(private val bufferBody: Boolean = true) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        return try {
            chain.proceed(request).bufferedIfWanted()
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            chain.proceed(request).bufferedIfWanted()
        }
    }

    private fun Response.bufferedIfWanted(): Response = if (bufferBody) buffered() else this

    /** Liest den Body komplett, damit ein Abbruch hier auffällt und nicht erst im Converter. */
    private fun Response.buffered(): Response {
        val body = body ?: return this
        val bytes = body.use { it.bytes() }
        return newBuilder().body(bytes.toResponseBody(body.contentType())).build()
    }
}

internal const val AUTHORIZATION = "Authorization"

internal fun bearer(token: String) = "Bearer $token"
