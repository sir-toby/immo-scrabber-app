package de.immoscrabber.app.core.session

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Ergebnis von [normalizeBaseUrl]. */
sealed interface BaseUrlResult {
    /** Normalisierte Basis-URL, endet immer auf `/api/`. */
    data class Valid(val url: String) : BaseUrlResult

    /** `http://` ist nicht erlaubt (Release: nie; Debug: nur Emulator-Host/localhost). */
    data object HttpsRequired : BaseUrlResult

    /** Leer oder keine brauchbare URL. */
    data object Invalid : BaseUrlResult
}

/** Hosts, die im Debug-Build per `http://` erreichbar sein dürfen (lokales Backend). */
private val LOCAL_CLEARTEXT_HOSTS = setOf("10.0.2.2", "localhost")

private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")

/**
 * Normalisiert die Server-Eingabe des Login-Screens (Entscheidung #7): ohne Schema wird
 * `https://` ergänzt, der Pfad endet immer auf `/api/`. Nur HTTPS; mit
 * [allowLocalCleartext] (Debug-Build) zusätzlich `http://` für `10.0.2.2` und `localhost`.
 */
fun normalizeBaseUrl(input: String, allowLocalCleartext: Boolean): BaseUrlResult {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return BaseUrlResult.Invalid
    val scheme = SCHEME.find(trimmed)?.groupValues?.get(1)?.lowercase()
    val withScheme = when (scheme) {
        null -> "https://$trimmed"
        "https", "http" -> scheme + trimmed.substring(scheme.length)
        else -> return BaseUrlResult.Invalid
    }
    val url = withScheme.toHttpUrlOrNull() ?: return BaseUrlResult.Invalid
    if (url.host.isBlank()) return BaseUrlResult.Invalid
    if (!url.isHttps && !(allowLocalCleartext && url.host in LOCAL_CLEARTEXT_HOSTS)) {
        return BaseUrlResult.HttpsRequired
    }
    val segments = url.pathSegments.filter { it.isNotEmpty() }
    val apiSegments = if (segments.lastOrNull() == "api") segments else segments + "api"
    val normalized = url.newBuilder()
        .encodedPath("/")
        .apply { apiSegments.forEach { addPathSegment(it) } }
        .addPathSegment("")
        .query(null)
        .fragment(null)
        .build()
    return BaseUrlResult.Valid(normalized.toString())
}
