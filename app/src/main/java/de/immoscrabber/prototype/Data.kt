// PROTOTYPE – Wegwerf-Code. In-Memory-Daten statt API, damit das Swipe-Verhalten ohne Backend beurteilt werden kann.
package de.immoscrabber.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.NumberFormat
import java.util.Locale

enum class Label(val api: String, val title: String) {
    OPEN("unbewertet", "Neu"),
    INTERESTING("interessant", "Favorit"),
    UNINTERESTING("uninteressant", "Archiv"),
}

enum class Filter(val title: String, val label: Label?) {
    NEU("Neu", Label.OPEN),
    FAVORITEN("Favoriten", Label.INTERESTING),
    ALLE("Alle", null),
    ARCHIV("Archiv", Label.UNINTERESTING),
}

enum class PType(val title: String, val api: String) {
    HOUSE("Häuser", "house"),
    FLAT("Wohnungen", "flat"),
    SITE("Grundstücke", "site"),
}

data class Listing(
    val id: Int,
    val type: PType,
    val title: String,
    val zipCode: String?,
    val city: String?,
    val rooms: Double?,
    val constructionYear: Int?,
    val areaBuilding: Double?,
    val areaEstate: Double?,
    val price: Double?,
    val energyClass: String?,
    val source: String,
    val url: String,
    val label: Label = Label.OPEN,
)

private val euro = NumberFormat.getIntegerInstance(Locale.GERMANY)
fun Listing.priceText() = price?.let { "${euro.format(it)} €" } ?: "Preis auf Anfrage"
fun Listing.placeText() = listOfNotNull(zipCode, city).joinToString(" ").ifBlank { "Ort unbekannt" }
fun Listing.factsText(): String = when (type) {
    PType.HOUSE -> listOfNotNull(
        rooms?.let { "${fmt(it)} Zi." }, areaBuilding?.let { "${fmt(it)} m² Wfl." },
        areaEstate?.let { "${fmt(it)} m² Grund" }, constructionYear?.let { "Bj. $it" },
    )
    PType.FLAT -> listOfNotNull(
        rooms?.let { "${fmt(it)} Zi." }, areaBuilding?.let { "${fmt(it)} m²" }, constructionYear?.let { "Bj. $it" },
    )
    PType.SITE -> listOfNotNull(areaEstate?.let { "${fmt(it)} m² Grund" })
}.joinToString(" · ")

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString().replace('.', ',')

/** Hält den Zustand im Speicher und protokolliert, welcher API-Call in der echten App passieren würde. */
class Store {
    val listings = mutableStateListOf<Listing>().apply { addAll(sampleListings()) }
    private val history = mutableStateListOf<Pair<Listing, Int>>()
    var lastAction by mutableStateOf("–")
        private set

    fun visible(type: PType, filter: Filter) =
        listings.filter { it.type == type && (filter.label == null || it.label == filter.label) }

    fun count(type: PType, label: Label) = listings.count { it.type == type && it.label == label }

    fun rate(listing: Listing, label: Label, moveToEnd: Boolean = false) {
        val idx = listings.indexOfFirst { it.id == listing.id }
        if (idx < 0) return
        val old = listings[idx]
        history.add(old to idx)
        val updated = old.copy(label = label)
        if (moveToEnd) {
            listings.removeAt(idx); listings.add(updated)
        } else {
            listings[idx] = updated
        }
        lastAction = if (old.label == label) "#${old.id}: unverändert (${label.api}) – kein API-Call"
        else "PATCH /properties/${old.type.api}/${old.id}/label {label: ${label.api}}"
    }

    fun skip(listing: Listing) {
        val idx = listings.indexOfFirst { it.id == listing.id }
        if (idx < 0) return
        history.add(listings[idx] to idx)
        listings.add(listings.removeAt(idx))
        lastAction = "#${listing.id}: übersprungen – bleibt unbewertet, kein API-Call"
    }

    val canUndo get() = history.isNotEmpty()

    fun undo() {
        val (old, oldIdx) = history.removeLastOrNull() ?: return
        listings.removeAll { it.id == old.id }
        listings.add(oldIdx.coerceAtMost(listings.size), old)
        lastAction = "Rückgängig: PATCH /properties/${old.type.api}/${old.id}/label {label: ${old.label.api}}"
    }
}

private val cities = listOf(
    "91052" to "Erlangen", "91054" to "Erlangen", "90402" to "Nürnberg", "90762" to "Fürth",
    "91074" to "Herzogenaurach", "91083" to "Baiersdorf", "91088" to "Bubenreuth", "91080" to "Uttenreuth",
    "91091" to "Großenseebach", "91325" to "Adelsdorf", "91085" to "Weisendorf", null to null,
)
private val sources = listOf(
    "Sparkasse", "VR-Bank Erlangen", "Kleinanzeigen", "Ohne-Makler", "Interhyp", "Deutsche Bank",
    "Ilona Wolf", "Myhome-Makler", "Schweidler", "NIB", "ZVG-Portal",
)
private val houseTitles = listOf(
    "Charmantes Einfamilienhaus mit großem Garten", "Doppelhaushälfte in ruhiger Lage",
    "Reihenmittelhaus – ideal für junge Familien", "Modernisierungsbedürftiges Siedlungshaus",
    "Freistehendes EFH mit Einliegerwohnung", "Bungalow auf sonnigem Eckgrundstück",
    "Zwangsversteigerung: Einfamilienhaus mit Garage", "Neubau-Doppelhaus KfW 40",
    "Fachwerkhaus im Ortskern", "Architektenhaus mit Blick ins Grüne",
)
private val flatTitles = listOf(
    "3-Zimmer-Wohnung mit Südbalkon", "Helle Dachgeschosswohnung", "Erdgeschoss mit Gartenanteil",
    "Penthouse mit Dachterrasse", "Gepflegte 2-Zimmer-Wohnung nahe Uni", "4-Zimmer-Maisonette",
    "Kapitalanlage: vermietete 1-Zimmer-Wohnung", "Neubauwohnung mit Tiefgarage",
)
private val siteTitles = listOf(
    "Baugrundstück in Neubaugebiet", "Grundstück mit Altbestand", "Bauplatz für Doppelhaus",
    "Erschlossenes Baugrundstück, bauträgerfrei", "Hanggrundstück mit Fernblick", "Kleines Grundstück für Tiny House",
)

private fun sampleListings(): List<Listing> {
    val rnd = kotlin.random.Random(42)
    var id = 1000
    fun city() = cities[rnd.nextInt(cities.size)]
    fun <T> maybe(v: T, pNull: Double = 0.15) = if (rnd.nextDouble() < pNull) null else v
    val out = mutableListOf<Listing>()
    houseTitles.forEach { t ->
        val (zip, c) = city()
        out += Listing(
            ++id, PType.HOUSE, t, zip, c, maybe(rnd.nextInt(4, 9).toDouble()), maybe(rnd.nextInt(1955, 2025)),
            maybe(rnd.nextInt(95, 230).toDouble()), maybe(rnd.nextInt(250, 1100).toDouble()),
            maybe(rnd.nextInt(38, 120) * 10_000.0), maybe(listOf("A+", "A", "B", "C", "D", "E", "F", "G", "H").random(rnd), 0.3),
            sources.random(rnd), "https://example.org/expose/$id",
        )
    }
    flatTitles.forEach { t ->
        val (zip, c) = city()
        out += Listing(
            ++id, PType.FLAT, t, zip, c, maybe(listOf(1.0, 2.0, 2.5, 3.0, 3.5, 4.0).random(rnd)), maybe(rnd.nextInt(1965, 2025)),
            maybe(rnd.nextInt(30, 140).toDouble()), null, maybe(rnd.nextInt(15, 70) * 10_000.0), null,
            sources.random(rnd), "https://example.org/expose/$id",
        )
    }
    siteTitles.forEach { t ->
        val (zip, c) = city()
        out += Listing(
            ++id, PType.SITE, t, zip, c, null, null, null, maybe(rnd.nextInt(180, 1200).toDouble(), 0.05),
            maybe(rnd.nextInt(12, 60) * 10_000.0), null, sources.random(rnd), "https://example.org/expose/$id",
        )
    }
    // ein paar schon bewertete, damit Favoriten/Archiv nicht leer sind
    return out.mapIndexed { i, l ->
        when (i % 5) {
            3 -> l.copy(label = Label.INTERESTING)
            4 -> l.copy(label = Label.UNINTERESTING)
            else -> l
        }
    }
}
