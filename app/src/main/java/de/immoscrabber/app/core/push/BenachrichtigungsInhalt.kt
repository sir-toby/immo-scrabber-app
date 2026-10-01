package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.model.PropertyType

/**
 * Was in der Benachrichtigung über Neuzugänge steht (Entscheidung #9): Titel „3 neue Häuser“,
 * bis zu drei Zeilen „Ort · Preis · Fläche“ und „+ n weitere“. Pro Typ gibt es einen Channel und
 * genau eine Benachrichtigung ([notificationId]); eine neue ersetzt die alte des Typs.
 */
data class BenachrichtigungsInhalt(
    val channelId: String,
    val notificationId: Int,
    val titel: String,
    val zeilen: List<String>,
) {
    companion object {
        fun aus(nachricht: PushNachricht): BenachrichtigungsInhalt {
            val weitere = nachricht.count - nachricht.lines.size
            return BenachrichtigungsInhalt(
                channelId = channelId(nachricht.type),
                notificationId = NOTIFICATION_ID_BASIS + nachricht.type.ordinal,
                titel = titel(nachricht.type, nachricht.count),
                zeilen = nachricht.lines + listOfNotNull(weitere.takeIf { it > 0 }?.let { "+ $it weitere" }),
            )
        }

        /** Channel-ID je Typ; bleibt stabil, sonst verlieren Nutzer ihre Channel-Einstellungen. */
        fun channelId(type: PropertyType): String = when (type) {
            PropertyType.HOUSE -> "neue_haeuser"
            PropertyType.FLAT -> "neue_wohnungen"
            PropertyType.SITE -> "neue_grundstuecke"
        }

        private fun titel(type: PropertyType, count: Int): String {
            val (einzahl, mehrzahl) = when (type) {
                PropertyType.HOUSE -> "neues Haus" to "neue Häuser"
                PropertyType.FLAT -> "neue Wohnung" to "neue Wohnungen"
                PropertyType.SITE -> "neues Grundstück" to "neue Grundstücke"
            }
            return "$count ${if (count == 1) einzahl else mehrzahl}"
        }

        private const val NOTIFICATION_ID_BASIS = 1000
    }
}
