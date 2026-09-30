package de.immoscrabber.app.core.model

/** Wert einer Bewertung. [apiValue] ist die Schreibweise des Backends. */
enum class Label(val apiValue: String) {
    UNBEWERTET("unbewertet"),
    INTERESSANT("interessant"),
    UNINTERESSANT("uninteressant"),
    ;

    companion object {
        /** Unbekannte oder fehlende Werte gelten als [UNBEWERTET] (so liefert es der Server ohne Relation). */
        fun fromApiValue(value: String?): Label =
            entries.firstOrNull { it.apiValue == value } ?: UNBEWERTET
    }
}
