package de.immoscrabber.app.settings

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import de.immoscrabber.app.core.network.ApiError
import java.time.LocalDate

/** Default-Umkreis eines neuen Suchprofils in km (Entscheidung #8). */
const val DEFAULT_RADIUS_KM = 20

/** Felder des Editors; welche ein Typ zeigt, sagt [felderFuer]. */
enum class Feld { Typ, Plz, Stadt, Radius, Preis, Zimmer, BaujahrVon, BaujahrBis, Flaeche, Anbieter }

enum class Feldfehler { TypFehlt, PlzUngueltig, StadtFehlt, RadiusUngueltig, KeineGanzeZahl, BaujahrUngueltig, BaujahrReihenfolge }

private val IMMER = setOf(Feld.Typ, Feld.Plz, Feld.Stadt, Feld.Radius)

/**
 * Felder je Typ wie im Web (`SearchFormFields.vue`, Entscheidung #8). Ausgeschlossene Anbieter nur
 * bei Haus und Wohnung, weil das Backend sie auf Grundstücke nicht anwendet.
 */
fun felderFuer(type: PropertyType?): Set<Feld> = IMMER + when (type) {
    PropertyType.HOUSE -> setOf(Feld.Preis, Feld.Zimmer, Feld.BaujahrVon, Feld.BaujahrBis, Feld.Flaeche, Feld.Anbieter)
    PropertyType.FLAT -> setOf(Feld.Preis, Feld.Zimmer, Feld.BaujahrVon, Feld.Anbieter)
    PropertyType.SITE -> setOf(Feld.Preis, Feld.Flaeche)
    null -> emptySet()
}

/**
 * Eingaben des Suchprofil-Editors, so wie getippt (Texte). [anbieterEingabe] ist der noch nicht als
 * Chip bestätigte Text im Anbieter-Feld; beim Speichern zählt er mit.
 */
data class SuchprofilFormular(
    val type: PropertyType?,
    val zipCode: String = "",
    val city: String = "",
    val radius: String = DEFAULT_RADIUS_KM.toString(),
    val priceLimit: String = "",
    val minRooms: String = "",
    val minConstructionYear: String = "",
    val maxConstructionYear: String = "",
    val minArea: String = "",
    val ausgeschlosseneAnbieter: List<String> = emptyList(),
    val anbieterEingabe: String = "",
) {
    val felder: Set<Feld> get() = felderFuer(type)

    /**
     * Fehler je sichtbarem Feld; leer heißt speicherbar. Ein leeres Limit ist kein Fehler (kein Limit).
     * Baujahre gelten zwischen [MIN_BAUJAHR] und [aktuellesJahr] + [BAUJAHR_VORLAUF] (Neubauprojekte).
     */
    fun pruefen(aktuellesJahr: Int = LocalDate.now().year): Map<Feld, Feldfehler> = buildMap {
        val baujahre = MIN_BAUJAHR..aktuellesJahr + BAUJAHR_VORLAUF
        if (type == null) put(Feld.Typ, Feldfehler.TypFehlt)
        if (!PLZ.matches(zipCode.trim())) put(Feld.Plz, Feldfehler.PlzUngueltig)
        if (city.isBlank()) put(Feld.Stadt, Feldfehler.StadtFehlt)
        if ((radius.trim().toIntOrNull() ?: 0) <= 0) put(Feld.Radius, Feldfehler.RadiusUngueltig)
        val felder = felder
        for ((feld, wert) in listOf(Feld.Preis to priceLimit, Feld.Zimmer to minRooms, Feld.Flaeche to minArea)) {
            if (feld in felder && !optionaleZahl(wert)) put(feld, Feldfehler.KeineGanzeZahl)
        }
        val von = baujahr(Feld.BaujahrVon, minConstructionYear, baujahre)
        val bis = baujahr(Feld.BaujahrBis, maxConstructionYear, baujahre)
        if (von != null && bis != null && von > bis) put(Feld.BaujahrBis, Feldfehler.BaujahrReihenfolge)
    }

    /** Prüft ein Baujahr (nur wenn sichtbar), trägt den Fehler ein und liefert das Jahr, falls gültig. */
    private fun MutableMap<Feld, Feldfehler>.baujahr(feld: Feld, wert: String, plausibel: IntRange): Int? {
        if (feld !in felder || wert.isBlank()) return null
        val jahr = wert.trim().takeIf { BAUJAHR.matches(it) }?.toInt()?.takeIf { it in plausibel }
        if (jahr == null) put(feld, Feldfehler.BaujahrUngueltig)
        return jahr
    }

    /** Ohne Leerzeichen am Rand: Danach vergleicht der Editor, ob sich inhaltlich etwas geändert hat. */
    fun normalisiert(): SuchprofilFormular = copy(
        zipCode = zipCode.trim(),
        city = city.trim(),
        radius = radius.trim(),
        priceLimit = priceLimit.trim(),
        minRooms = minRooms.trim(),
        minConstructionYear = minConstructionYear.trim(),
        maxConstructionYear = maxConstructionYear.trim(),
        minArea = minArea.trim(),
        anbieterEingabe = anbieterEingabe.trim(),
    )

    /**
     * Der Request zum Speichern, `null` solange [pruefen] Fehler meldet. Leere Felder werden `null`
     * (kein Limit). Was der Editor beim Typ nicht zeigt, bleibt wie in [original] (beim neuen Profil
     * leer): Der Editor ändert nur, was er zeigt. `source_blacklist` hat keine UI und bleibt immer.
     */
    fun toInput(original: Suchprofil?): SuchprofilInput? {
        val type = type ?: return null
        if (pruefen().isNotEmpty()) return null
        val felder = felder
        fun zahl(feld: Feld, wert: String, alt: Int?): Int? =
            if (feld in felder) wert.trim().takeIf(String::isNotEmpty)?.toInt() else alt
        return SuchprofilInput(
            propertyType = type,
            city = city.trim(),
            zipCode = zipCode.trim(),
            radius = radius.trim().toInt(),
            ausgeschlosseneAnbieter = if (Feld.Anbieter in felder) {
                anbieterMitEingabe()
            } else {
                original?.ausgeschlosseneAnbieter.orEmpty()
            },
            excludedSources = original?.excludedSources.orEmpty(),
            priceLimit = zahl(Feld.Preis, priceLimit, original?.priceLimit),
            minRooms = zahl(Feld.Zimmer, minRooms, original?.minRooms),
            minConstructionYear = zahl(Feld.BaujahrVon, minConstructionYear, original?.minConstructionYear),
            maxConstructionYear = zahl(Feld.BaujahrBis, maxConstructionYear, original?.maxConstructionYear),
            minArea = zahl(Feld.Flaeche, minArea, original?.minArea),
        )
    }

    /** Bestätigt [anbieterEingabe] als Chip (getrimmt, ohne Doppelte, Groß-/Kleinschreibung egal). */
    fun anbieterHinzufuegen(): SuchprofilFormular = copy(ausgeschlosseneAnbieter = anbieterMitEingabe(), anbieterEingabe = "")

    private fun anbieterMitEingabe(): List<String> {
        val neu = anbieterEingabe.trim()
        val vorhanden = ausgeschlosseneAnbieter.any { it.equals(neu, ignoreCase = true) }
        return if (neu.isEmpty() || vorhanden) ausgeschlosseneAnbieter else ausgeschlosseneAnbieter + neu
    }

    companion object {
        /** Neues Profil; [type] ist vorausgewählt (aus dem Tab) oder `null`. */
        fun neu(type: PropertyType?) = SuchprofilFormular(type = type)

        /** Formular zum Bearbeiten von [profil]. */
        fun aus(profil: Suchprofil) = SuchprofilFormular(
            type = profil.propertyType,
            zipCode = profil.zipCode.orEmpty(),
            city = profil.city.orEmpty(),
            radius = profil.radius?.toString().orEmpty(),
            priceLimit = profil.priceLimit?.toString().orEmpty(),
            minRooms = profil.minRooms?.toString().orEmpty(),
            minConstructionYear = profil.minConstructionYear?.toString().orEmpty(),
            maxConstructionYear = profil.maxConstructionYear?.toString().orEmpty(),
            minArea = profil.minArea?.toString().orEmpty(),
            ausgeschlosseneAnbieter = profil.ausgeschlosseneAnbieter,
        )
    }
}

private const val MIN_BAUJAHR = 1800
private const val BAUJAHR_VORLAUF = 5

private val PLZ = Regex("\\d{5}")
private val BAUJAHR = Regex("\\d{4}")
private val GANZE_ZAHL = Regex("\\d+")

private fun optionaleZahl(wert: String): Boolean {
    val t = wert.trim()
    return t.isEmpty() || (GANZE_ZAHL.matches(t) && t.toIntOrNull() != null)
}

/**
 * Neues Profil oder geänderter Ort (PLZ/Stadt): Dann lautet die Snackbar „… neue Inserate für diesen
 * Ort erscheinen nach dem nächsten Suchlauf“ (Entscheidung #8). Groß-/Kleinschreibung und
 * Leerzeichen zählen nicht, weil das Backend den Ort ohnehin geocodiert.
 */
fun ortGeaendert(original: Suchprofil?, input: SuchprofilInput): Boolean {
    if (original == null) return true
    fun gleich(a: String?, b: String) = a.orEmpty().trim().equals(b.trim(), ignoreCase = true)
    return !gleich(original.zipCode, input.zipCode) || !gleich(original.city, input.city)
}

/** Fehler beim Speichern oder Löschen; die Texte stehen in den String-Ressourcen. */
sealed interface EditorFehler {
    /** 500 beim Speichern: bewusst eine Heuristik für Geocoding-Fehler (Entscheidung #8). */
    data object OrtNichtGefunden : EditorFehler

    /** 400 mit Servertext (etwa das Limit von 10 Suchprofilen). */
    data class Server(val text: String) : EditorFehler

    data object NichtErreichbar : EditorFehler

    data class Unerwartet(val httpCode: Int?) : EditorFehler
}

fun speicherFehler(error: ApiError): EditorFehler = when {
    error is ApiError.Http && error.code == HTTP_INTERNAL_ERROR -> EditorFehler.OrtNichtGefunden
    error is ApiError.BadRequest && error.serverMessage != null -> EditorFehler.Server(error.serverMessage)
    else -> allgemeinerFehler(error)
}

fun loeschFehler(error: ApiError): EditorFehler = allgemeinerFehler(error)

private fun allgemeinerFehler(error: ApiError): EditorFehler = when (error) {
    is ApiError.Network -> EditorFehler.NichtErreichbar
    is ApiError.BadRequest -> EditorFehler.Unerwartet(HTTP_BAD_REQUEST)
    is ApiError.Http -> EditorFehler.Unerwartet(error.code)
    // Sitzungsende: der Wechsel zum Login folgt dem Sitzungszustand.
    ApiError.SessionExpired, is ApiError.InvalidResponse -> EditorFehler.Unerwartet(null)
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_INTERNAL_ERROR = 500
