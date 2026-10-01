package de.immoscrabber.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    /** Neues Profil: Typ per Segmented Buttons wählbar; sonst ist der Typ fix. */
    val neu: Boolean,
    val form: SuchprofilFormular,
    /** Fehler je Feld, erst nach dem ersten Speichern-Versuch (dann live). */
    val fehler: Map<Feld, Feldfehler> = emptyMap(),
    /** Speichern oder Löschen läuft: Formular gesperrt, Spinner. */
    val aktion: Aktion? = null,
    /** Abweichung vom Ausgangszustand: Beim Verlassen fragt „Änderungen verwerfen?“. */
    val geaendert: Boolean = false,
    /** Das Profil ist nicht (mehr) im Speicher, etwa nach dem Beenden durch das System. */
    val nichtGefunden: Boolean = false,
) {
    val gesperrt: Boolean get() = aktion != null
}

enum class Aktion { Speichern, Loeschen }

sealed interface EditorEvent {
    /** Gespeichert, Liste neu geladen; der Editor schließt mit Snackbar. */
    data class Gespeichert(val neuerOrt: Boolean) : EditorEvent

    data object Geloescht : EditorEvent

    /** Speichern/Löschen gescheitert; das Formular bleibt befüllt. */
    data class Fehler(val fehler: EditorFehler, val aktion: Aktion) : EditorEvent
}

/**
 * Suchprofil-Editor (Entscheidung #8). Speichern nur online, ohne Warteschlange; Neuladen der Liste
 * und Veraltet-Merker übernimmt das [repository].
 *
 * @param profilId das zu bearbeitende Profil aus [SuchprofilRepository.suchprofile]; `null` = neu.
 * @param vorauswahl Typ eines neuen Profils (aus dem Tab „Noch kein Suchprofil“), sonst `null`.
 */
class SuchprofilEditorViewModel(
    private val repository: SuchprofilRepository,
    profilId: String?,
    vorauswahl: PropertyType?,
) : ViewModel() {
    private val original: Suchprofil? = profilId?.let { id -> repository.suchprofile.value?.firstOrNull { it.id == id } }
    private val ausgang = original?.let(SuchprofilFormular::aus) ?: SuchprofilFormular.neu(vorauswahl)
    private var pruefen = false

    private val _state = MutableStateFlow(
        EditorUiState(neu = profilId == null, form = ausgang, nichtGefunden = profilId != null && original == null),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    fun aendern(transform: (SuchprofilFormular) -> SuchprofilFormular) {
        _state.update { state ->
            if (state.gesperrt) return@update state
            val neu = transform(state.form).let { if (state.neu) it else it.copy(type = ausgang.type) }
            state.copy(
                form = neu,
                fehler = if (pruefen) neu.pruefen() else emptyMap(),
                geaendert = neu != ausgang,
            )
        }
    }

    fun speichern() {
        val state = _state.value
        if (state.gesperrt || state.nichtGefunden) return
        pruefen = true
        val input = state.form.toInput(original)
        if (input == null) {
            _state.update { it.copy(fehler = it.form.pruefen()) }
            return
        }
        ausfuehren(Aktion.Speichern) {
            when (val result = repository.speichern(original?.id, input)) {
                is ApiResult.Success -> EditorEvent.Gespeichert(neuerOrt = ortGeaendert(original, input))
                is ApiResult.Failure -> EditorEvent.Fehler(speicherFehler(result.error), Aktion.Speichern)
            }
        }
    }

    fun loeschen() {
        val profil = original ?: return
        if (_state.value.gesperrt) return
        ausfuehren(Aktion.Loeschen) {
            when (val result = repository.loeschen(profil)) {
                is ApiResult.Success -> EditorEvent.Geloescht
                is ApiResult.Failure -> EditorEvent.Fehler(loeschFehler(result.error), Aktion.Loeschen)
            }
        }
    }

    private fun ausfuehren(aktion: Aktion, block: suspend () -> EditorEvent) {
        _state.update { it.copy(aktion = aktion, fehler = emptyMap()) }
        viewModelScope.launch {
            val event = block()
            // Nach Erfolg bleibt das Formular gesperrt, bis der Editor geschlossen ist.
            if (event is EditorEvent.Fehler) _state.update { it.copy(aktion = null) }
            _events.send(event)
        }
    }
}
