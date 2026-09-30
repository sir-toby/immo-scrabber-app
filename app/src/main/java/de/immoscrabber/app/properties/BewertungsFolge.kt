package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Label

/**
 * Hält die Bewertungen hinausfliegender Karten in Wischreihenfolge (#51). Die Karte dahinter ist
 * sofort oben; eine schnell gewischte Karte kann also landen, bevor die langsamer fliegende davor
 * gelandet ist. Ihre Bewertung wartet dann, damit Snackbar und „Rückgängig“ die zuletzt gewischte
 * Karte meinen.
 *
 * Karten werden über [key] erkannt, nicht über Gleichheit: Ein Refresh kann dieselbe Karte mit
 * geändertem Inhalt liefern, während sie fliegt.
 */
internal class BewertungsFolge<T>(private val key: (T) -> Any) {

    private class Eintrag<T>(val key: Any, var item: T) {
        var label: Label? = null
    }

    private val fliegend = ArrayDeque<Eintrag<T>>()

    /** Die Bewertung von [item] ist entschieden, die Karte fliegt los. */
    fun start(item: T) {
        fliegend.addLast(Eintrag(key(item), item))
    }

    /** [item] ist gelandet; liefert die Bewertungen, die jetzt der Reihe nach fällig sind. */
    fun finish(item: T, label: Label): List<Pair<T, Label>> {
        val itemKey = key(item)
        val eintrag = fliegend.firstOrNull { it.key == itemKey } ?: return listOf(item to label)
        eintrag.item = item
        eintrag.label = label
        return faellige()
    }

    /**
     * Vergisst noch fliegende Karten, deren Schlüssel nicht mehr in [keys] (dem Stapel) steht, etwa
     * nach einem Refresh; so kann eine Karte, deren Flug nie endet, die übrigen nicht aufhalten.
     * Schon gelandete Karten bleiben stehen: Ihre Bewertung ist entschieden und geht in Reihenfolge
     * raus. Liefert die Bewertungen, die dadurch fällig werden.
     */
    fun retain(keys: Set<Any>): List<Pair<T, Label>> {
        fliegend.removeAll { it.label == null && it.key !in keys }
        return faellige()
    }

    private fun faellige(): List<Pair<T, Label>> {
        val faellig = mutableListOf<Pair<T, Label>>()
        while (true) {
            val kopf = fliegend.firstOrNull() ?: break
            val kopfLabel = kopf.label ?: break
            fliegend.removeFirst()
            faellig += kopf.item to kopfLabel
        }
        return faellig
    }
}
