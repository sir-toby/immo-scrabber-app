package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Label

/**
 * Hält die Bewertungen hinausfliegender Karten in Wischreihenfolge (#51). Die Karte dahinter ist
 * sofort oben; eine schnell gewischte Karte kann also landen, bevor die langsamer fliegende davor
 * gelandet ist. Ihre Bewertung wartet dann, damit Snackbar und „Rückgängig“ die zuletzt gewischte
 * Karte meinen.
 */
internal class BewertungsFolge<T> {

    private class Eintrag<T>(val item: T) {
        var label: Label? = null
    }

    private val fliegend = ArrayDeque<Eintrag<T>>()

    /** Die Bewertung von [item] ist entschieden, die Karte fliegt los. */
    fun start(item: T) {
        fliegend.addLast(Eintrag(item))
    }

    /** [item] ist gelandet; liefert die Bewertungen, die jetzt der Reihe nach fällig sind. */
    fun finish(item: T, label: Label): List<Pair<T, Label>> {
        val eintrag = fliegend.firstOrNull { it.item == item } ?: return listOf(item to label)
        eintrag.label = label
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
