package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.model.PropertyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

/**
 * „Veraltet“-Merker je Immobilientyp (Entscheidung #10), für genau eine Sitzung (hängt an
 * `Session.veraltet` und wird mit ihr verworfen).
 *
 * Gesetzt wird er
 * - für **alle** Typen, wenn die App mehr als [threshold] im Hintergrund war ([onBackground] /
 *   [onForeground], verdrahtet im `AppContainer`), und
 * - für **einen** Typ per [markStale], wenn ein Suchprofil dieses Typs gespeichert oder gelöscht
 *   wurde (ruft der Suchprofil-Editor, #32).
 *
 * Der sichtbare Tab beobachtet [stale] und holt sich seinen Merker mit [consume] ab; dann lädt er
 * neu. Andere Tabs holen ihn beim nächsten Besuch ab.
 *
 * @param now monotone Uhr (in der App `SystemClock.elapsedRealtime`), in Tests von Hand gestellt.
 * @param threshold ab mehr als so langer Zeit im Hintergrund ist alles veraltet. Die 30 Minuten
 *   (Entscheidung #10) stehen nur in `app/build.gradle.kts` (`STALE_AFTER_SECONDS`, siehe `Session.veraltet`).
 */
class VeraltetMerker(
    private val now: () -> Duration,
    private val threshold: Duration,
) {
    private val _stale = MutableStateFlow<Set<PropertyType>>(emptySet())

    /** Die Typen, deren Liste neu geladen werden muss. */
    val stale: StateFlow<Set<PropertyType>> = _stale.asStateFlow()

    /** Seit wann die App im Hintergrund ist; `null` im Vordergrund. */
    private var backgroundSince: Duration? = null

    /** Ein Suchprofil von [type] wurde gespeichert oder gelöscht: nur diese Liste ist veraltet. */
    fun markStale(type: PropertyType) {
        _stale.update { it + type }
    }

    /** Die App ging in den Hintergrund. */
    fun onBackground() {
        backgroundSince = now()
    }

    /** Die App kam in den Vordergrund; nach mehr als [threshold] ist alles veraltet. */
    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (now() - since > threshold) _stale.value = PropertyType.entries.toSet()
    }

    /** Holt den Merker von [type] ab: `true` (und gelöscht), wenn die Liste neu laden muss. */
    fun consume(type: PropertyType): Boolean {
        var wasStale = false
        _stale.update { current ->
            wasStale = type in current
            current - type
        }
        return wasStale
    }
}
