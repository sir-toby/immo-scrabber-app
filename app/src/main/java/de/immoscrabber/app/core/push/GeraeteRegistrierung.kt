package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.network.ImmoApi
import de.immoscrabber.app.core.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Meldet das FCM-Token dieser Installation beim Backend an und ab (Entscheidung #9):
 * `PUT /devices` nach dem Login, bei jedem App-Start und bei neuem Token; `DELETE /devices/<token>`
 * beim Logout, best effort. Alles ist folgenlos, wenn es scheitert (kein FCM-Token ohne
 * Play-Dienste, Server nicht erreichbar); der nächste App-Start versucht es erneut.
 *
 * @param fcmToken liefert das aktuelle FCM-Token; darf werfen.
 * @param api API der laufenden Sitzung, `null` wenn abgemeldet.
 * @param abmeldeTimeoutMillis so lange wartet der Logout höchstens auf das `DELETE`.
 */
class GeraeteRegistrierung(
    private val fcmToken: suspend () -> String,
    private val api: () -> ImmoApi?,
    private val abmeldeTimeoutMillis: Long = DEFAULT_ABMELDE_TIMEOUT_MILLIS,
) {
    /**
     * Registriert bei jedem Wechsel auf angemeldet: Sitzung aus dem Speicher beim App-Start
     * und jeder Login. Läuft, solange der Aufrufer-Scope lebt.
     */
    suspend fun folgeSitzung(state: Flow<SessionState>) {
        state.filterIsInstance<SessionState.LoggedIn>().collect { registrieren() }
    }

    /** `PUT /devices` mit dem aktuellen Token, wenn angemeldet. */
    suspend fun registrieren() {
        val token = aktuellesToken() ?: return
        api()?.geraetRegistrieren(token)
    }

    /** Aus `onNewToken`: das neue Token gleich registrieren, wenn angemeldet. */
    suspend fun neuesToken(token: String) {
        api()?.geraetRegistrieren(token)
    }

    /** Vor dem Logout (solange die Sitzung noch gilt): `DELETE /devices/<token>`, höchstens kurz. */
    suspend fun abmelden() {
        val api = api() ?: return
        withTimeoutOrNull(abmeldeTimeoutMillis) {
            val token = aktuellesToken() ?: return@withTimeoutOrNull
            api.geraetAbmelden(token)
        }
    }

    private suspend fun aktuellesToken(): String? =
        try {
            fcmToken().takeIf(String::isNotBlank)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }

    private companion object {
        const val DEFAULT_ABMELDE_TIMEOUT_MILLIS = 3_000L
    }
}
