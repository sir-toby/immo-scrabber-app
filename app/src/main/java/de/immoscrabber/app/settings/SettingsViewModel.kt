package de.immoscrabber.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.immoscrabber.app.core.data.MAX_SUCHPROFILE
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sektion „Suchprofile“ (Entscheidung #8). */
sealed interface SuchprofileState {
    /** Erstes Laden: Spinner. */
    data object Laden : SuchprofileState

    /** Erstes Laden gescheitert: „Suchprofile konnten nicht geladen werden“ + „Erneut versuchen“. */
    data object Fehler : SuchprofileState

    /** [profile] schon sortiert (Typ in Tab-Reihenfolge, dann Stadt). */
    data class Geladen(val profile: List<Suchprofil>) : SuchprofileState {
        /** Bei 10/10 ersetzt „Maximal 10 Suchprofile“ den Eintrag „+ Neues Suchprofil“. */
        val maximumErreicht: Boolean get() = profile.size >= MAX_SUCHPROFILE
    }
}

data class SettingsUiState(
    val suchprofile: SuchprofileState = SuchprofileState.Laden,
    /** Pull-to-Refresh läuft; die alte Liste bleibt solange stehen. */
    val refreshing: Boolean = false,
    /** Logout läuft (Gerät abmelden dauert bis zu 3 s): Dialog zeigt Fortschritt, weitere Taps zählen nicht. */
    val abmelden: Boolean = false,
)

sealed interface SettingsEvent {
    /** Pull-to-Refresh gescheitert, die alte Liste bleibt: Snackbar. */
    data object RefreshFailed : SettingsEvent
}

private enum class Ladestatus { Ruhe, ErstesLaden, ErstesLadenFehlgeschlagen, Aktualisieren }

/**
 * Einstellungen-Tab: Suchprofile aus dem Repository der Sitzung (beim ersten Öffnen geladen,
 * danach im Speicher, Entscheidung #10) und Abmelden.
 *
 * @param logout meldet lokal ab (Tokens und Caches weg); der Wechsel zum Login folgt dem Sitzungszustand.
 */
class SettingsViewModel(
    private val repository: SuchprofilRepository,
    private val logout: suspend () -> Unit,
) : ViewModel() {
    private val status = MutableStateFlow(Ladestatus.Ruhe)
    private val abmeldenLaeuft = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(repository.suchprofile, status, abmeldenLaeuft, ::uiState)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            uiState(repository.suchprofile.value, status.value, abmeldenLaeuft.value),
        )

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        if (repository.suchprofile.value == null) load(Ladestatus.ErstesLaden)
    }

    /** „Erneut versuchen“ nach gescheitertem ersten Laden. */
    fun retry() {
        if (status.value == Ladestatus.ErstesLadenFehlgeschlagen) {
            load(Ladestatus.ErstesLaden)
        }
    }

    /** Pull-to-Refresh; ohne geladene Liste wie „Erneut versuchen“. */
    fun refresh() {
        if (status.value == Ladestatus.ErstesLaden || status.value == Ladestatus.Aktualisieren) return
        load(if (repository.suchprofile.value == null) Ladestatus.ErstesLaden else Ladestatus.Aktualisieren)
    }

    /** Nur einmal gleichzeitig; ein zweiter Tap während des Logouts wird ignoriert. */
    fun abmelden() {
        if (!abmeldenLaeuft.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                logout()
            } finally {
                abmeldenLaeuft.value = false
            }
        }
    }

    private fun load(kind: Ladestatus) {
        status.value = kind
        viewModelScope.launch {
            val result = repository.laden()
            status.value = when {
                result is ApiResult.Success -> Ladestatus.Ruhe
                kind == Ladestatus.Aktualisieren -> {
                    _events.trySend(SettingsEvent.RefreshFailed)
                    Ladestatus.Ruhe
                }
                else -> Ladestatus.ErstesLadenFehlgeschlagen
            }
        }
    }
}

private fun uiState(profile: List<Suchprofil>?, status: Ladestatus, abmelden: Boolean) = SettingsUiState(
    suchprofile = when {
        profile != null -> SuchprofileState.Geladen(sortiereSuchprofile(profile))
        status == Ladestatus.ErstesLadenFehlgeschlagen -> SuchprofileState.Fehler
        else -> SuchprofileState.Laden
    },
    refreshing = status == Ladestatus.Aktualisieren,
    abmelden = abmelden,
)
