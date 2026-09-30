package de.immoscrabber.app.core.network

import java.io.IOException

/** Fehler eines API-Aufrufs, unabhängig vom uneinheitlichen Fehlerformat des Backends. */
sealed interface ApiError {
    /** Server nicht erreichbar, Timeout, Verbindung abgebrochen. */
    data class Network(val cause: IOException) : ApiError

    /**
     * 401 oder 422 (JWT-Fehler von flask_jwt_extended). Mit dem Refresh-Interceptor der Sitzung
     * kommt das erst an, wenn auch der Refresh gescheitert ist. Bei `login` nur 401: Benutzername
     * oder Passwort falsch.
     */
    data object SessionExpired : ApiError

    /** 400; [serverMessage] aus `Error`, `error` oder `msg`, falls der Body JSON ist. */
    data class BadRequest(val serverMessage: String?) : ApiError

    /** Jeder andere HTTP-Fehlerstatus (404, 5xx, HTML-Fehlerseiten …). */
    data class Http(val code: Int) : ApiError

    /** 2xx, aber der Body passt nicht zum erwarteten Format (oder ein Pflichtfeld fehlt). */
    data class InvalidResponse(val cause: Throwable) : ApiError
}
