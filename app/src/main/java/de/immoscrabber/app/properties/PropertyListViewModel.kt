package de.immoscrabber.app.properties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.immoscrabber.app.core.data.InseratRepository
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Der Kartenstapel lädt nach, sobald weniger als so viele Karten übrig sind (Entscheidung #6). */
private const val STACK_LOAD_MORE_THRESHOLD = 5

/** Filter-Chips eines Tabs; [label] ist der Query-Wert (`null` = alle Labels). */
enum class Filter(val label: Label?) {
    Neu(Label.UNBEWERTET),
    Favoriten(Label.INTERESSANT),
    Alle(null),
    Archiv(Label.UNINTERESSANT),
}

/** Eine abgeschickte Bewertung, damit Rückgängig und Fehler sie zurückdrehen können. */
class Bewertung internal constructor(
    internal val inserat: Inserat,
    internal val label: Label,
    internal val pager: InseratPager,
    internal val removed: RemovedInserat?,
) {
    internal var job: Job? = null
    internal var failed = false
}

/** Einmalige Ereignisse des Tabs. */
sealed interface ListEvent {
    /** „Als Favorit markiert“ / „Ins Archiv verschoben“ mit „Rückgängig“. */
    data class Bewertet(val label: Label, val bewertung: Bewertung) : ListEvent

    /** „Bewertung nicht gespeichert“ mit „Erneut versuchen“; die Zeile ist schon zurück. */
    data class BewertungFehlgeschlagen(val inserat: Inserat, val label: Label) : ListEvent

    /** „Rückgängig nicht möglich“; das Inserat bleibt bewertet. */
    data object UndoFailed : ListEvent

    /** Inserat im Custom Tab öffnen. */
    data class OpenLink(val url: String) : ListEvent

    /** „Kein Link zum Inserat“. */
    data object NoLink : ListEvent

    /** „37 Häuser ins Archiv verschoben“, ohne Rückgängig (der Endpunkt liefert keine IDs). */
    data class BulkArchived(val count: Int) : ListEvent

    /** „Alle als uninteressant markieren“ ist gescheitert; der Stapel bleibt. */
    data object BulkArchiveFailed : ListEvent
}

data class PropertyListUiState(
    val filter: Filter = Filter.Neu,
    val pager: PagerState = PagerState(),
)

/**
 * Ein Tab für einen Immobilientyp: Filter, Liste des aktiven Filters, Bewerten.
 * Je Tab eine Instanz (am Back-Stack-Eintrag des Tabs), deshalb überlebt sie Tab-Wechsel und Drehen.
 */
class PropertyListViewModel(
    val type: PropertyType,
    private val repository: InseratRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PropertyListUiState())
    val state: StateFlow<PropertyListUiState> = _state.asStateFlow()

    private val _events = Channel<ListEvent>(Channel.BUFFERED)

    /** Einmalige Ereignisse für Snackbars und das Öffnen des Inserats. */
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    /** Nur die letzte Bewertung lässt sich rückgängig machen. */
    private var letzteBewertung: Bewertung? = null

    private lateinit var pager: InseratPager
    private var pagerScope: CoroutineScope? = null

    init {
        startPager(Filter.Neu)
    }

    /** Filterwechsel lädt frisch (Entscheidung #10); die alte Liste und ihre Ladevorgänge verfallen. */
    fun selectFilter(filter: Filter) {
        if (filter == _state.value.filter) return
        startPager(filter)
    }

    /**
     * Bewertet optimistisch (Entscheidung #6): Verlässt das Inserat den Filter, fliegt die Zeile
     * sofort heraus, sonst (unter „Alle“) wechselt nur das Label. Ein Wisch zum aktuellen Label
     * tut nichts.
     */
    fun bewerten(inserat: Inserat, label: Label) {
        if (inserat.label == label) return
        val filterLabel = _state.value.filter.label
        val leavesList = filterLabel != null && filterLabel != label
        val target = pager
        val removed = if (leavesList) {
            target.remove(inserat.id)
        } else {
            target.update(inserat.id) { it.copy(label = label) }
            null
        }
        val bewertung = Bewertung(inserat, label, target, removed)
        letzteBewertung = bewertung
        _events.trySend(ListEvent.Bewertet(label, bewertung))
        bewertung.job = viewModelScope.launch {
            if (repository.bewerten(type, inserat.id, label) is ApiResult.Failure) {
                // Keine stillen Wiederholungen: zurückdrehen, melden, der Nutzer entscheidet.
                bewertung.failed = true
                if (letzteBewertung === bewertung) letzteBewertung = null
                revert(bewertung)
                _events.send(ListEvent.BewertungFehlgeschlagen(inserat, label))
            }
        }
    }

    /** „Erneut versuchen“ nach einem gescheiterten PATCH. */
    fun retry(failed: ListEvent.BewertungFehlgeschlagen) = bewerten(failed.inserat, failed.label)

    /**
     * „Rückgängig“ für die letzte Bewertung: setzt das alte Label per PATCH und holt erst danach
     * die Zeile zurück (bzw. das alte Label unter „Alle“). Scheitert das, bleibt das Inserat
     * bewertet (Entscheidung #6). Wartet auf den ursprünglichen PATCH, damit beide nicht
     * gegeneinander laufen.
     */
    fun undo(bewertet: ListEvent.Bewertet) {
        val bewertung = bewertet.bewertung
        if (bewertung !== letzteBewertung) return
        letzteBewertung = null
        viewModelScope.launch {
            bewertung.job?.join()
            if (bewertung.failed) return@launch
            when (repository.bewerten(type, bewertung.inserat.id, bewertung.inserat.label)) {
                is ApiResult.Success -> revert(bewertung)
                is ApiResult.Failure -> _events.send(ListEvent.UndoFailed)
            }
        }
    }

    /**
     * „Überspringen“ im Kartenstapel: Die Karte bleibt unbewertet und wandert ans Ende des
     * geladenen Stapels, nur im Speicher (Entscheidung #6).
     */
    fun skip(inserat: Inserat) {
        pager.moveToEnd(inserat.id)
    }

    /**
     * „Alle als uninteressant markieren“ (⋮-Menü unter „Neu“, nach dem Bestätigungsdialog):
     * archiviert serverseitig genau die „Neu“-Liste des Typs und lädt den Stapel danach neu.
     * Kein Rückgängig; auch die letzte Einzelbewertung ist danach nicht mehr rückgängig zu machen.
     */
    fun archiveAllNew() {
        viewModelScope.launch {
            when (val result = repository.alleNeuenBewerten(type, Label.UNINTERESSANT)) {
                is ApiResult.Success -> {
                    letzteBewertung = null
                    _events.send(ListEvent.BulkArchived(result.value))
                    if (_state.value.filter == Filter.Neu) pager.refresh()
                }
                is ApiResult.Failure -> _events.send(ListEvent.BulkArchiveFailed)
            }
        }
    }

    /** Tipp auf eine Zeile: Inserat öffnen oder „Kein Link zum Inserat“. */
    fun open(inserat: Inserat) {
        val url = inserat.url?.trim()
        _events.trySend(if (url.isNullOrEmpty()) ListEvent.NoLink else ListEvent.OpenLink(url))
    }

    /** Pull-to-Refresh: verwirft den Cursor und lädt von oben. */
    fun refresh() = pager.refresh()

    /** Automatisches Nachladen kurz vor dem Listenende. */
    fun loadMore() = pager.loadMore()

    /** „Erneut versuchen“ nach einem Ladefehler (leere Ansicht oder Listenende). */
    fun retryLoading() = pager.retry()

    private fun revert(bewertung: Bewertung) {
        if (bewertung.removed != null) {
            bewertung.pager.restore(bewertung.removed)
        } else {
            bewertung.pager.update(bewertung.inserat.id) {
                if (it.label == bewertung.label) it.copy(label = bewertung.inserat.label) else it
            }
        }
    }

    private fun startPager(filter: Filter) {
        pagerScope?.cancel()
        val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
        pagerScope = scope
        val newPager = InseratPager(scope) { cursor -> repository.seite(type, filter.label, cursor) }
        pager = newPager
        _state.value = PropertyListUiState(filter, newPager.state.value)
        scope.launch {
            newPager.state.collect { pagerState ->
                _state.update { it.copy(pager = pagerState) }
                // Kartenstapel: still nachladen, sobald weniger als 5 Karten übrig sind. Der Pager
                // ignoriert das während des Ladens, am Ende und nach einem Fehler (dann „Erneut versuchen“).
                if (filter == Filter.Neu && pagerState.items.size < STACK_LOAD_MORE_THRESHOLD) newPager.loadMore()
            }
        }
        newPager.refresh()
    }
}
