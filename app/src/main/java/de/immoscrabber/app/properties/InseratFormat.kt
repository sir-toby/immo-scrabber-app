package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.PropertyType
import java.math.RoundingMode
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
fun formatFacts(inserat: Inserat): String = detailFacts(inserat)
    .filter { inserat.propertyType == PropertyType.SITE || it.kind != FactKind.PRICE_PER_SQUARE_METER }
    .joinToString(SEPARATOR) { it.text }

/** Art einer Eckdate; das Detail-Sheet zeigt je Art ein Symbol. */
enum class FactKind { ROOMS, LIVING_AREA, PLOT_AREA, CONSTRUCTION_YEAR, PRICE_PER_SQUARE_METER }

/** Eine Eckdate mit fertigem Text, z. B. `Fact(ROOMS, "5 Zi")`. */
data class Fact(val kind: FactKind, val text: String)

/**
 * Eckdaten-Chips des Detail-Sheets (#14), Texte wie in [formatFacts]; fehlende Werte fallen weg.
 * Zusätzlich €/m² für alle Typen: Haus und Wohnung auf die Wohnfläche, Grundstück auf die Fläche.
 */
fun detailFacts(inserat: Inserat): List<Fact> = when (inserat.propertyType) {
    PropertyType.HOUSE -> listOfNotNull(
        rooms(inserat)?.let { Fact(FactKind.ROOMS, it) },
        inserat.livingArea.positive()?.let { Fact(FactKind.LIVING_AREA, "${area(it)} Wfl.") },
        inserat.plotArea.positive()?.let { Fact(FactKind.PLOT_AREA, "${area(it)} Grundst.") },
        constructionYear(inserat)?.let { Fact(FactKind.CONSTRUCTION_YEAR, it) },
        pricePerSquareMeterFact(inserat.price, inserat.livingArea),
    )
    PropertyType.FLAT -> listOfNotNull(
        rooms(inserat)?.let { Fact(FactKind.ROOMS, it) },
        inserat.livingArea.positive()?.let { Fact(FactKind.LIVING_AREA, area(it)) },
        constructionYear(inserat)?.let { Fact(FactKind.CONSTRUCTION_YEAR, it) },
        pricePerSquareMeterFact(inserat.price, inserat.livingArea),
    )
    PropertyType.SITE -> listOfNotNull(
        inserat.plotArea.positive()?.let { Fact(FactKind.PLOT_AREA, area(it)) },
        pricePerSquareMeterFact(inserat.price, inserat.plotArea),
    )
}

private fun pricePerSquareMeterFact(price: Double?, area: Double?): Fact? =
    pricePerSquareMeter(price, area)?.let { Fact(FactKind.PRICE_PER_SQUARE_METER, "${formatNumber(it, 0)} €/m²") }

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

/** Adresse im Detail-Sheet: „Straße Hausnummer, PLZ Ort“; fehlende Teile fallen weg, eine Hausnummer ohne Straße auch. */
fun formatAddress(inserat: Inserat): String {
    val street = inserat.street.clean()?.let { street -> listOfNotNull(street, inserat.houseNumber.clean()).joinToString(" ") }
    return listOfNotNull(street, formatPlace(inserat).ifEmpty { null }).joinToString(", ")
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

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
        // Kaufmännisch (416,5 → 417), nicht die Standardrundung HALF_EVEN.
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
