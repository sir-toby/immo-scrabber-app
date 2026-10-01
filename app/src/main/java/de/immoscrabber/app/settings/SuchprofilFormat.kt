package de.immoscrabber.app.settings

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import java.net.URI
import java.text.Collator
import java.text.NumberFormat
import java.util.Locale

// Reine Formatierung der Suchprofil-Liste (Entscheidung #8). Die App ist nur deutsch,
// deshalb stehen Einheiten und Kürzel hier und nicht in den String-Ressourcen.

private const val SEPARATOR = " · "

/** „Erlangen (+20 km)“; ohne Stadt die PLZ, ohne Umkreis nur der Ort. */
fun formatSuchprofilTitel(profil: Suchprofil): String {
    val ort = listOf(profil.city, profil.zipCode)
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
        ?: "Unbekannter Ort"
    return profil.radius?.let { "$ort (+$it km)" } ?: ort
}

/**
 * Zweite Zeile: die gesetzten Limits („bis 600.000 € · ab 4 Zi. · Bj. 1990–2020“), sonst
 * „ohne Limits“. Die Fläche ist beim Haus die Grundstücksfläche („ab 500 m² Grundst.“).
 */
fun formatLimits(profil: Suchprofil): String {
    val teile = listOfNotNull(
        profil.priceLimit?.let { "bis ${formatNumber(it)} €" },
        profil.minRooms?.let { "ab $it Zi." },
        baujahr(profil.minConstructionYear, profil.maxConstructionYear),
        profil.minArea?.let {
            if (profil.propertyType == PropertyType.HOUSE) "ab ${formatNumber(it)} m² Grundst." else "ab ${formatNumber(it)} m²"
        },
    )
    return if (teile.isEmpty()) "ohne Limits" else teile.joinToString(SEPARATOR)
}

private fun baujahr(von: Int?, bis: Int?): String? = when {
    von != null && bis != null -> "Bj. $von–$bis"
    von != null -> "Bj. ab $von"
    bis != null -> "Bj. bis $bis"
    else -> null
}

/**
 * Nach Immobilientyp in Tab-Reihenfolge (Häuser → Wohnungen → Grundstücke, unbekannte zuletzt),
 * innerhalb eines Typs alphabetisch nach Stadt (deutsche Sortierung, Groß-/Kleinschreibung egal).
 */
fun sortiereSuchprofile(profile: List<Suchprofil>): List<Suchprofil> {
    val collator = Collator.getInstance(Locale.GERMANY).apply { strength = Collator.SECONDARY }
    return profile.sortedWith(
        compareBy<Suchprofil> { it.propertyType?.ordinal ?: Int.MAX_VALUE }
            .then { a, b -> collator.compare(a.city.orEmpty(), b.city.orEmpty()) },
    )
}

/** Server für „Angemeldet als *user* auf *host*“: Host samt abweichendem Port, ohne Schema und Pfad. */
fun formatHost(baseUrl: String): String {
    val uri = runCatching { URI(baseUrl.trim()) }.getOrNull()
    val host = uri?.host ?: return baseUrl
    return if (uri.port == -1) host else "$host:${uri.port}"
}

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(Locale.GERMANY).format(value)
