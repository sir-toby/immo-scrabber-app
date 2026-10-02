package de.immoscrabber.app.properties

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * Entscheidet beim Loslassen, was eine Berührung der obersten Karte war (#51).
 *
 * Unter Last liefert Android mitunter nur Aufsetzen und Loslassen, ohne Bewegung dazwischen.
 * `clickable` prüft dann nur, ob der Finger noch auf der Karte liegt, und `draggable` erkennt ohne
 * Bewegung keine Touch-Slop: Ein Wisch käme als Tippen an. Deshalb zählt ein Tippen nur, wenn der
 * Finger beim Loslassen höchstens die Touch-Slop vom Aufsetzpunkt entfernt ist. Ein waagrechter
 * Sprung ohne gemeldete Bewegung wird wie ein Wisch behandelt.
 *
 * Long Press (#14, Detail-Sheet): Der Finger bleibt bis [longPressTimeoutMillis] innerhalb der
 * Touch-Slop, bevor eine Achse entschieden ist. Danach zählt die Geste weder als Tippen noch als Wisch.
 *
 * Positionen in Koordinaten der Karte; ohne Ziehen steht die Karte still, sie entsprechen also
 * dem Bildschirm.
 */
internal class KartenGeste(private val touchSlop: Float, private val longPressTimeoutMillis: Long = 500L) {

    sealed interface Ende {
        /** Der Finger hat sich nicht bewegt: Inserat öffnen. */
        data object Tippen : Ende

        /**
         * Waagrechte Bewegung kam an; `draggable` hat die Geste übernommen. [restDx] ist die
         * Strecke, die erst mit dem Loslassen ankam: `draggable` meldet sie nicht, unter Last ist
         * sie groß.
         */
        data class Gezogen(val restDx: Float) : Ende

        /** Waagrecht weit weg losgelassen, ohne dass Bewegung ankam: wie ein Wisch um [dx] (px, px/s). */
        data class Sprung(val dx: Float, val velocity: Float) : Ende

        /**
         * Senkrecht weit weg losgelassen, ohne dass Bewegung ankam: weder öffnen noch bewerten.
         * Das Loslassen muss verbraucht werden, sonst öffnet `clickable`.
         */
        data object Verworfen : Ende

        /**
         * Senkrechte Bewegung kam an; sie gehört dem Scrollen bzw. Pull-to-Refresh, das auch
         * `clickable` abbrechen lässt. Das Loslassen bleibt unverbraucht.
         */
        data object Gescrollt : Ende

        /**
         * Long Press: Das Sheet ist offen (oder öffnet jetzt, wenn [erstBeimLoslassen], weil das
         * Loslassen vor dem Timer ankam). Das Loslassen wird verbraucht, sonst öffnet `clickable`.
         */
        data class LangGedrueckt(val erstBeimLoslassen: Boolean) : Ende
    }

    private var downPosition = Offset.Zero
    private var downTime = 0L
    private var movedVertically = false
    private var movedHorizontally = false
    private var lastPosition = Offset.Zero
    private var longPressed = false

    fun down(position: Offset, uptimeMillis: Long) {
        downPosition = position
        downTime = uptimeMillis
        movedVertically = false
        movedHorizontally = false
        lastPosition = position
        longPressed = false
    }

    /** Die Achse, die zuerst die Touch-Slop überschreitet, entscheidet (wie bei Scrollen/Ziehen). */
    fun move(position: Offset) {
        lastPosition = position
        if (longPressed || movedVertically || movedHorizontally) return
        val delta = position - downPosition
        when {
            abs(delta.x) > touchSlop && abs(delta.x) >= abs(delta.y) -> movedHorizontally = true
            abs(delta.y) > touchSlop -> movedVertically = true
        }
    }

    /**
     * `true` genau einmal, sobald der Finger bis [uptimeMillis] lange genug still lag: Jetzt das Sheet
     * öffnen. Danach entscheidet keine Bewegung mehr über Wisch oder Scrollen.
     */
    fun longPress(uptimeMillis: Long): Boolean {
        if (longPressed || !still(lastPosition) || uptimeMillis - downTime < longPressTimeoutMillis) return false
        longPressed = true
        return true
    }

    private fun still(position: Offset) =
        !movedVertically && !movedHorizontally && (position - downPosition).getDistance() <= touchSlop

    /** [step]: Bewegung seit dem letzten Ereignis, die das Loslassen selbst mitbringt. */
    fun up(position: Offset, uptimeMillis: Long, step: Offset = Offset.Zero): Ende {
        val dx = position.x - downPosition.x
        return when {
            longPressed -> Ende.LangGedrueckt(erstBeimLoslassen = false)
            still(position) && uptimeMillis - downTime >= longPressTimeoutMillis -> Ende.LangGedrueckt(erstBeimLoslassen = true)
            movedHorizontally -> Ende.Gezogen(restDx = step.x)
            movedVertically -> Ende.Gescrollt
            (position - downPosition).getDistance() <= touchSlop -> Ende.Tippen
            abs(dx) > touchSlop -> {
                val millis = (uptimeMillis - downTime).coerceAtLeast(1L)
                Ende.Sprung(dx = dx, velocity = dx * 1_000f / millis)
            }
            else -> Ende.Verworfen
        }
    }
}
