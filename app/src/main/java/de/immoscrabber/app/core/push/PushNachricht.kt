package de.immoscrabber.app.core.push

import de.immoscrabber.app.core.model.PropertyType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Inhalt einer FCM-Data-Message über Neuzugänge (Entscheidung #9, Backend `scraper/notifier.py`):
 * `type` (`house`/`flat`/`site`), `count` (Anzahl als Text) und `lines` (JSON-Array mit bis zu drei
 * Zeilen „Ort · Preis · Fläche“, die neuesten zuerst).
 */
data class PushNachricht(val type: PropertyType, val count: Int, val lines: List<String>) {
    companion object {
        /**
         * Liest die Data-Message robust: ohne erkennbaren Typ oder positive Anzahl `null`
         * (dann zeigt die App nichts), kaputte Zeilen werden weggelassen.
         */
        fun parse(data: Map<String, String>): PushNachricht? {
            val type = PropertyType.fromApiValue(data["type"]) ?: return null
            val count = data["count"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            return PushNachricht(type, count, parseLines(data["lines"]))
        }

        private fun parseLines(raw: String?): List<String> {
            if (raw == null) return emptyList()
            val array = try {
                Json.parseToJsonElement(raw) as? JsonArray
            } catch (_: SerializationException) {
                null
            } ?: return emptyList()
            return array
                .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim() }
                .filter(String::isNotEmpty)
                .take(MAX_ZEILEN)
        }
    }
}

/** Höchstens so viele Inserate stehen einzeln in der Benachrichtigung. */
const val MAX_ZEILEN = 3
