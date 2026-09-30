package de.immoscrabber.app.core.model

/**
 * Ein Inserat aus `GET /properties/results`. Außer [id], [propertyType] und [label] kann
 * jedes Feld fehlen, weil die Scraper nur füllen, was der Anbieter hergibt.
 */
data class Inserat(
    val id: String,
    val propertyType: PropertyType,
    val title: String?,
    /** Rohe Bild-URL beim Anbieter: kann `http://`, relativ oder `data:` sein. */
    val imageUrl: String?,
    val price: Double?,
    val zipCode: String?,
    val city: String?,
    val street: String?,
    val houseNumber: String?,
    /** Nur Häuser und Wohnungen. */
    val rooms: Double?,
    /** Wohnfläche in m² (`area_building`), nur Häuser und Wohnungen. */
    val livingArea: Double?,
    /** Grundstücksfläche in m² (`area_estate`). */
    val plotArea: Double?,
    /** Anbieter (Makler, Bank …), nur Häuser und Wohnungen. */
    val anbieter: String?,
    /** Link zum Inserat bei der Quelle. */
    val url: String?,
    /** Quelle, also das Portal, z. B. „Kleinanzeigen“. */
    val source: String?,
    /** Naiver UTC-Zeitstempel des Servers, unverändert (dient auch als Cursor). */
    val createdAt: String?,
    val label: Label,
    /** Nur Häuser und Wohnungen. */
    val constructionYear: Int?,
    /** Nur Häuser und Wohnungen. */
    val energyEfficiencyClass: String?,
)

/** Position nach dem letzten Inserat einer Seite; der Server sortiert `created_at DESC, id DESC`. */
data class PageCursor(val createdAt: String, val id: String)

/** Eine Seite Inserate. [nextCursor] ist `null`, wenn es keine weitere Seite gibt. */
data class InseratPage(val inserate: List<Inserat>, val nextCursor: PageCursor?)
