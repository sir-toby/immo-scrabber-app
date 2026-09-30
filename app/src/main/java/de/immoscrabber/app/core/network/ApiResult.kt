package de.immoscrabber.app.core.network

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

/** Ergebnis eines API-Aufrufs: entweder der Wert oder ein [ApiError]. Wirft nie. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/**
 * Führt einen Retrofit-Aufruf aus und übersetzt jedes Scheitern in einen [ApiError].
 * Die einzige Stelle, an der Fehler der API übersetzt werden (Entscheidung #10).
 * Abbruch der Coroutine wird durchgereicht. [sessionExpiredCodes] sind die Status, die als
 * [ApiError.SessionExpired] gelten.
 */
internal suspend fun <T> apiCall(
    sessionExpiredCodes: Set<Int> = JWT_ERROR_CODES,
    block: suspend () -> T,
): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ApiResult.Failure(httpError(e.code(), e.response()?.errorBody()?.string(), sessionExpiredCodes))
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network(e))
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.InvalidResponse(e))
    } catch (e: InvalidResponseException) {
        ApiResult.Failure(ApiError.InvalidResponse(e))
    }

/** Signalisiert aus einem [apiCall]-Block heraus eine unbrauchbare 2xx-Antwort. */
internal class InvalidResponseException(message: String) : RuntimeException(message)

internal fun <T : Any> T?.required(field: String): T =
    this ?: throw InvalidResponseException("Pflichtfeld fehlt: $field")

/** 401 und 422 sind JWT-Fehler von flask_jwt_extended. */
internal val JWT_ERROR_CODES = setOf(401, 422)

private fun httpError(code: Int, body: String?, sessionExpiredCodes: Set<Int>): ApiError = when (code) {
    in sessionExpiredCodes -> ApiError.SessionExpired
    400 -> ApiError.BadRequest(serverMessage(body))
    else -> ApiError.Http(code)
}

/** Fehlertext aus `{"Error": …}`, `{"error": …}` oder `{"msg": …}`; `null` bei Nicht-JSON. */
private fun serverMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val json = try {
        ApiJson.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return null
    return listOf("Error", "error", "msg").firstNotNullOfOrNull { key ->
        (json[key] as? JsonPrimitive)?.contentOrNull
    }
}
