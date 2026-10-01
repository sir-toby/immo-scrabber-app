package de.immoscrabber.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.immoscrabber.app.core.push.Benachrichtigungen
import de.immoscrabber.app.core.push.BenachrichtigungsInhalt
import de.immoscrabber.app.core.push.PushNachricht
import de.immoscrabber.app.core.session.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Empfängt die FCM-Data-Messages über Neuzugänge (Entscheidung #9) und baut daraus die
 * Benachrichtigung. Ohne Sitzung zeigt die App nichts an.
 */
class ImmoMessagingService : FirebaseMessagingService() {
    private val container get() = (application as ImmoFinderApp).container

    override fun onNewToken(token: String) {
        container.neuesFcmToken(token)
    }

    /** Läuft auf einem Hintergrund-Thread; FCM gibt dafür einige Sekunden. */
    override fun onMessageReceived(message: RemoteMessage) {
        val nachricht = PushNachricht.parse(message.data) ?: return
        if (!sitzungAktiv()) return
        Benachrichtigungen(this).zeigen(BenachrichtigungsInhalt.aus(nachricht))
    }

    /** Bei kaltem Prozess liest der SessionManager den Speicher noch; kurz darauf warten. */
    private fun sitzungAktiv(): Boolean = runBlocking {
        val state = withTimeoutOrNull(SITZUNG_TIMEOUT_MILLIS) {
            container.sessionManager.state.first { it != SessionState.Loading }
        }
        state is SessionState.LoggedIn
    }

    private companion object {
        const val SITZUNG_TIMEOUT_MILLIS = 5_000L
    }
}
