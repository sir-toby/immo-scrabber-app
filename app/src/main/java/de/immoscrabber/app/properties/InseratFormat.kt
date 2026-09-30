package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.PropertyType
import java.text.NumberFormat
import java.util.Locale

// Reine Formatierung der Felder von Karte und Zeile (Entscheidung #6). Die App ist nur deutsch,
// deshalb stehen Einheiten und Kürzel hier und nicht in den String-Ressourcen.

private const val SEPARATOR = " · "

/** „450.000 €“; ohne (oder mit 0) Preis „Preis auf Anfrage“. */
fun formatPrice(price: Double?): String =
    if (price == null || price <= 0.0) "Preis auf Anfrage" else "${formatNumber(price, 0)} €"

/**
 * Eckdaten je Immobilientyp, getrennt mit „·“; fehlende Werte fallen weg:
 * - Haus: `5 Zi · 140 m² Wfl. · 600 m² Grundst. · Bj. 1978`
 * - Wohnung: `3 Zi · 85 m² · Bj. 1995` (Wohnfläche)
 * - Grundstück: `600 m² · 417 €/m²` (Grundstücksfläche; €/m² nur mit Preis und Fläche)
 */
fun formatFacts(inserat: Inserat): String = when (inserat.propertyType) {
    PropertyType.HOUSE -> listOfNotNull(
        rooms(inserat),
        inserat.livingArea.positive()?.let { "${area(it)} Wfl." },
        inserat.plotArea.positive()?.let { "${area(it)} Grundst." },
        constructionYear(inserat),
    )
    PropertyType.FLAT -> listOfNotNull(
        rooms(inserat),
        inserat.livingArea.positive()?.let(::area),
        constructionYear(inserat),
    )
    PropertyType.SITE -> listOfNotNull(
        inserat.plotArea.positive()?.let(::area),
        pricePerSquareMeter(inserat.price, inserat.plotArea)?.let { "${formatNumber(it, 0)} €/m²" },
    )
}.joinToString(SEPARATOR)

private fun rooms(inserat: Inserat) = inserat.rooms?.let { "${formatNumber(it, 1)} Zi" }

private fun constructionYear(inserat: Inserat) = inserat.constructionYear?.let { "Bj. $it" }

private fun area(squareMeters: Double) = "${formatNumber(squareMeters, 0)} m²"

/** Eine Fläche von 0 m² ist ein fehlender Wert und fällt weg. */
private fun Double?.positive(): Double? = this?.takeIf { it > 0.0 }

private fun pricePerSquareMeter(price: Double?, area: Double?): Double? =
    if (price == null || area == null || price <= 0.0 || area <= 0.0) null else price / area

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
