package de.immoscrabber.app.core.model

/**
 * Immobilientyp eines Inserats. Das Backend nutzt zwei Schreibweisen:
 * [apiValue] (Singular, Label-Endpoints und Suchprofile) und [queryValue] (Plural,
 * `propertyTypes` von `/properties/results` und die Schlüssel der Antwort).
 */
enum class PropertyType(val apiValue: String, val queryValue: String) {
    HOUSE("house", "houses"),
    FLAT("flat", "flats"),
    SITE("site", "sites"),
    ;

    companion object {
        /** Singular, Groß-/Kleinschreibung egal (Suchprofile kommen als `HOUSE`). */
        fun fromApiValue(value: String?): PropertyType? =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
    }
}
