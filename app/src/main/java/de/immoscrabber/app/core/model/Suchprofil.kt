package de.immoscrabber.app.core.model

/** Ein gespeichertes Suchprofil, wie es `GET /preferences` liefert. */
data class Suchprofil(
    val id: String,
    val propertyType: PropertyType?,
    val city: String?,
    val zipCode: String?,
    /** Umkreis in km. */
    val radius: Int?,
    /** Ausgeschlossene Anbieter (Teilstring-Match, nur Häuser und Wohnungen). */
    val excludedProviders: List<String>,
    /** Wird gespeichert, filtert laut Backend aber keine Ergebnisse. */
    val excludedSources: List<String>,
    val priceLimit: Int?,
    val minRooms: Int?,
    val minConstructionYear: Int?,
    val maxConstructionYear: Int?,
    val minArea: Int?,
)

/**
 * Inhalt eines Suchprofils zum Anlegen oder Ändern. Alle Felder werden geschickt, auch `null`
 * (der Server setzt dann den Wert zurück). Stadt, PLZ und Umkreis prüft der Aufrufer vorher,
 * sonst antwortet das Backend mit 500.
 */
data class SuchprofilInput(
    val propertyType: PropertyType,
    val city: String,
    val zipCode: String,
    val radius: Int,
    val excludedProviders: List<String> = emptyList(),
    val excludedSources: List<String> = emptyList(),
    val priceLimit: Int? = null,
    val minRooms: Int? = null,
    val minConstructionYear: Int? = null,
    val maxConstructionYear: Int? = null,
    val minArea: Int? = null,
)
