package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.network.ImmoApi
import de.immoscrabber.app.core.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Meldet das FCM-Token dieser Installation beim Backend an und ab (Entscheidung #9):
 * `PUT /devices` nach dem Login, bei jedem App-Start und bei neuem Token; `DELETE /devices/<token>`
 * beim Logout, best effort. Alles ist folgenlos, wenn es scheitert (kein FCM-Token ohne
 * Play-Dienste, Server nicht erreichbar); der nächste App-Start versucht es erneut.
 *
 * @param sessionState Anmeldezustand; solange er `Loading` ist, steht noch nicht fest, ob es eine Sitzung gibt.
 * @param fcmToken liefert das aktuelle FCM-Token; darf werfen.
 * @param api API der laufenden Sitzung, `null` wenn abgemeldet.
 * @param abmeldeTimeoutMillis so lange wartet der Logout höchstens auf das `DELETE`.
 */
class GeraeteRegistrierung(
    private val sessionState: Flow<SessionState>,
    private val fcmToken: suspend () -> String,
    private val api: () -> ImmoApi?,
    private val abmeldeTimeoutMillis: Long = DEFAULT_ABMELDE_TIMEOUT_MILLIS,
) {
    /**
     * Registriert bei jedem Wechsel auf angemeldet: Sitzung aus dem Speicher beim App-Start
     * und jeder Login. Läuft, solange der Aufrufer-Scope lebt.
     */
    suspend fun folgeSitzung() {
        sessionState.filterIsInstance<SessionState.LoggedIn>().collect {
            // Ein unerwarteter Fehler darf das Folgen nicht beenden; der nächste Wechsel versucht es neu.
            folgenlos { registrieren() }
        }
    }

    /** `PUT /devices` mit dem aktuellen Token, wenn angemeldet. */
    suspend fun registrieren() {
        val token = aktuellesToken() ?: return
        api()?.geraetRegistrieren(token)
    }

    /**
     * Aus `onNewToken`: das neue Token registrieren, wenn angemeldet. Im kalten Prozess wird der
     * Sitzungsspeicher noch gelesen; erst danach steht fest, ob es eine Sitzung gibt. Abgemeldet
     * verfällt das Token (der nächste Login registriert ohnehin das aktuelle).
     */
    suspend fun neuesToken(token: String) {
        val state = sessionState.first { it != SessionState.Loading }
        if (state !is SessionState.LoggedIn) return
        folgenlos { api()?.geraetRegistrieren(token) }
    }

    /** Vor dem Logout (solange die Sitzung noch gilt): `DELETE /devices/<token>`, höchstens kurz. */
    suspend fun abmelden() {
        val api = api() ?: return
        withTimeoutOrNull(abmeldeTimeoutMillis) {
            val token = aktuellesToken() ?: return@withTimeoutOrNull
            api.geraetAbmelden(token)
        }
    }

    private suspend fun aktuellesToken(): String? = folgenlos { fcmToken().takeIf(String::isNotBlank) }

    /** Führt [block] aus; eine Ausnahme (außer Abbruch) ergibt `null`. */
    private suspend fun <T> folgenlos(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }

    private companion object {
        const val DEFAULT_ABMELDE_TIMEOUT_MILLIS = 3_000L
    }
}
