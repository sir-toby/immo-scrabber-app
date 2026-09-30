package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Inserat
import java.text.NumberFormat
import java.util.Locale

// Reine Formatierung der Felder von Karte und Zeile (Entscheidung #6). Die App ist nur deutsch,
// deshalb stehen Einheiten und Kürzel hier und nicht in den String-Ressourcen.

private const val SEPARATOR = " · "

/** „450.000 €“; ohne (oder mit 0) Preis „Preis auf Anfrage“. */
fun formatPrice(price: Double?): String =
    if (price == null || price <= 0.0) "Preis auf Anfrage" else "${formatNumber(price, 0)} €"

/**
 * Eckdaten, getrennt mit „·“; fehlende Werte fallen weg. Format für Häuser:
 * `5 Zi · 140 m² Wfl. · 600 m² Grundst. · Bj. 1978`.
 *
 * Wohnungen und Grundstücke bekommen ihr eigenes Format im Ticket ihrer Tabs; bis dahin gilt
 * auch für sie das Hausformat (bei ihnen fehlen die unpassenden Werte ohnehin).
 */
fun formatFacts(inserat: Inserat): String = listOfNotNull(
    inserat.rooms?.let { "${formatNumber(it, 1)} Zi" },
    inserat.livingArea?.let { "${formatNumber(it, 0)} m² Wfl." },
    inserat.plotArea?.let { "${formatNumber(it, 0)} m² Grundst." },
    inserat.constructionYear?.let { "Bj. $it" },
).joinToString(SEPARATOR)

/** „PLZ Ort“ (Karte im Kartenstapel); fehlende Teile fallen weg. */
fun formatPlace(inserat: Inserat): String = listOfNotNull(inserat.zipCode, inserat.city)
    .map(String::trim)
    .filter(String::isNotEmpty)
    .joinToString(" ")

/** Zeile der Wischliste: „PLZ Ort · Eckdaten“, fehlende Teile fallen weg. */
fun formatPlaceAndFacts(inserat: Inserat): String =
    listOf(formatPlace(inserat), formatFacts(inserat)).filter(String::isNotEmpty).joinToString(SEPARATOR)

/**
 * Zähler unter dem Kartenstapel (Entscheidung #6): „N übrig“, solange weitere Seiten existieren
 * „20+ übrig“ (die API nennt keine Gesamtzahl).
 */
fun formatRemaining(count: Int, moreAvailable: Boolean): String =
    if (moreAvailable) "20+ übrig" else "$count übrig"

/**
 * Lädbare Bild-URL oder `null` für den Platzhalter (Entscheidung #10): `https://` bleibt,
 * `http://` wird zu `https://` (Cleartext bleibt aus), alles andere (relativ, `data:`, leer)
 * ist unbrauchbar.
 */
fun thumbnailUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    val rest = when {
        url.startsWith("https://", ignoreCase = true) -> url.substring("https://".length)
        url.startsWith("http://", ignoreCase = true) -> url.substring("http://".length)
        else -> return null
    }
    return if (rest.isBlank()) null else "https://$rest"
}

private fun formatNumber(value: Double, maxFractionDigits: Int): String =
    NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        maximumFractionDigits = maxFractionDigits
        isGroupingUsed = true
    }.format(value)
