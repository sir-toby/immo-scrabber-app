package de.immoscrabber.app.properties

import androidx.compose.ui.geometry.Offset

/**
 * Entscheidet beim Loslassen, was eine Berührung der obersten Karte war (#51).
 *
 * Unter Last liefert Android mitunter nur Aufsetzen und Loslassen, ohne Bewegung dazwischen.
 * `clickable` prüft dann nur, ob der Finger noch auf der Karte liegt, und `draggable` erkennt ohne
 * Bewegung keine Touch-Slop: Ein Wisch käme als Tippen an. Deshalb zählt ein Tippen nur, wenn der
 * Finger beim Loslassen höchstens die Touch-Slop vom Aufsetzpunkt entfernt ist. Ein Sprung ohne
 * gemeldete Bewegung wird wie ein Wisch behandelt.
 *
 * Positionen in Koordinaten der Karte; ohne Ziehen steht die Karte still, sie entsprechen also
 * dem Bildschirm.
 */
internal class KartenGeste(private val touchSlop: Float) {

    sealed interface Ende {
        /** Der Finger hat sich nicht bewegt: Inserat öffnen. */
        data object Tippen : Ende

        /**
         * Die Bewegung kam an; `draggable` hat die Geste übernommen. [restDx] ist die Strecke, die
         * erst mit dem Loslassen ankam: `draggable` meldet sie nicht, unter Last ist sie groß.
         */
        data class Gezogen(val restDx: Float) : Ende

        /** Loslassen weit weg, ohne dass eine Bewegung ankam: wie ein Wisch um [dx] (px, px/s). */
        data class Sprung(val dx: Float, val velocity: Float) : Ende
    }

    private var downPosition = Offset.Zero
    private var downTime = 0L
    private var movedBeyondSlop = false

    fun down(position: Offset, uptimeMillis: Long) {
        downPosition = position
        downTime = uptimeMillis
        movedBeyondSlop = false
    }

    fun move(position: Offset) {
        if (beyondSlop(position)) movedBeyondSlop = true
    }

    /** [step]: Bewegung seit dem letzten Ereignis, die das Loslassen selbst mitbringt. */
    fun up(position: Offset, uptimeMillis: Long, step: Offset = Offset.Zero): Ende = when {
        movedBeyondSlop -> Ende.Gezogen(restDx = step.x)
        !beyondSlop(position) -> Ende.Tippen
        else -> {
            val dx = position.x - downPosition.x
            val millis = (uptimeMillis - downTime).coerceAtLeast(1L)
            Ende.Sprung(dx = dx, velocity = dx * 1_000f / millis)
        }
    }

    private fun beyondSlop(position: Offset) = (position - downPosition).getDistance() > touchSlop
}
