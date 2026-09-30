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

/** Filter-Chips eines Tabs; [label] ist der Query-Wert (`null` = alle Labels). */
enum class Filter(val label: Label?) {
    Neu(Label.UNBEWERTET),
    Favoriten(Label.INTERESSANT),
    Alle(null),
    Archiv(Label.UNINTERESSANT),
}

/** Eine abgeschickte Bewertung, damit Rückgängig und Fehler sie zurückdrehen können. */
class Rating internal constructor(
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
    data class Rated(val label: Label, val rating: Rating) : ListEvent

    /** „Bewertung nicht gespeichert“ mit „Erneut versuchen“; die Zeile ist schon zurück. */
    data class RatingFailed(val inserat: Inserat, val label: Label) : ListEvent

    /** „Rückgängig nicht möglich“; das Inserat bleibt bewertet. */
    data object UndoFailed : ListEvent

    /** Inserat im Custom Tab öffnen. */
    data class OpenLink(val url: String) : ListEvent

    /** „Kein Link zum Inserat“. */
    data object NoLink : ListEvent
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
    private var lastRating: Rating? = null

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
    fun rate(inserat: Inserat, label: Label) {
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
        val rating = Rating(inserat, label, target, removed)
        lastRating = rating
        _events.trySend(ListEvent.Rated(label, rating))
        rating.job = viewModelScope.launch {
            if (repository.bewerten(type, inserat.id, label) is ApiResult.Failure) {
                // Keine stillen Wiederholungen: zurückdrehen, melden, der Nutzer entscheidet.
                rating.failed = true
                if (lastRating === rating) lastRating = null
                revert(rating)
                _events.send(ListEvent.RatingFailed(inserat, label))
            }
        }
    }

    /** „Erneut versuchen“ nach einem gescheiterten PATCH. */
    fun retry(failed: ListEvent.RatingFailed) = rate(failed.inserat, failed.label)

    /**
     * „Rückgängig“ für die letzte Bewertung: setzt das alte Label per PATCH und holt erst danach
     * die Zeile zurück (bzw. das alte Label unter „Alle“). Scheitert das, bleibt das Inserat
     * bewertet (Entscheidung #6). Wartet auf den ursprünglichen PATCH, damit beide nicht
     * gegeneinander laufen.
     */
    fun undo(rated: ListEvent.Rated) {
        val rating = rated.rating
        if (rating !== lastRating) return
        lastRating = null
        viewModelScope.launch {
            rating.job?.join()
            if (rating.failed) return@launch
            when (repository.bewerten(type, rating.inserat.id, rating.inserat.label)) {
                is ApiResult.Success -> revert(rating)
                is ApiResult.Failure -> _events.send(ListEvent.UndoFailed)
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

    private fun revert(rating: Rating) {
        if (rating.removed != null) {
            rating.pager.restore(rating.removed)
        } else {
            rating.pager.update(rating.inserat.id) {
                if (it.label == rating.label) it.copy(label = rating.inserat.label) else it
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
        scope.launch { newPager.state.collect { pagerState -> _state.update { it.copy(pager = pagerState) } } }
        newPager.refresh()
    }
}
