package de.immoscrabber.app.properties

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.immoscrabber.app.core.data.InseratRepository
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.data.VeraltetMerker
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filter-Chips eines Tabs; [label] ist der Query-Wert (`null` = alle Labels). */
enum class Filter(val label: Label?) {
    Neu(Label.UNBEWERTET),
    Favoriten(Label.INTERESSANT),
    Alle(null),
    Archiv(Label.UNINTERESSANT),
}

/** Einmalige Ereignisse des Tabs. */
sealed interface ListEvent {
    /**
     * „Als Favorit markiert“ / „Ins Archiv verschoben“ / „Zurück zu Neu verschoben“ mit „Rückgängig“. [bewertungId] nennt die
     * Bewertung für [PropertyListViewModel.undo]; ihren Zustand hält das ViewModel.
     */
    data class Bewertet(val label: Label, val bewertungId: Long) : ListEvent

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

/** Schlüssel des Filters im [SavedStateHandle]; überlebt so das Beenden durch das System. */
internal const val FILTER_KEY = "filter"

/** Scrollposition einer Wischliste (erster sichtbarer Eintrag und sein Versatz in Pixeln). */
data class ListScroll(val index: Int = 0, val offset: Int = 0)

data class PropertyListUiState(
    val filter: Filter = Filter.Neu,
    val pager: PagerState = PagerState(),
    /**
     * Die Liste ist leer und es gibt Suchprofile, aber keins für diesen Typ: „Kein Suchprofil für
     * Grundstücke“ statt des normalen Leerzustands (#13).
     */
    val keinSuchprofilFuerTyp: Boolean = false,
    /** Die Liste ist leer und die Suchprofile werden gerade erstmals geladen (Spinner statt Leerzustand). */
    val suchprofilePruefen: Boolean = false,
)

/** Vollständig geladen und leer (nicht nur noch leer, weil die erste Seite fehlt). */
private val PagerState.leer: Boolean get() = loaded && endReached && items.isEmpty()

/**
 * Ein Tab für einen Immobilientyp: Filter, Liste des aktiven Filters, Bewerten.
 * Je Tab eine Instanz (am Back-Stack-Eintrag des Tabs), deshalb überlebt sie Tab-Wechsel und Drehen.
 *
 * Zustand über das Beenden durch das System (Entscheidung #10): Nur der Filter steht in
 * [savedState]; Liste und Scrollposition leben im ViewModel und beginnen danach von oben.
 *
 * @param suchprofile die Suchprofile der Sitzung; nur für den Leerzustand „Kein Suchprofil für …“.
 *   Der Tab nutzt deren Speicher und lädt nur, solange sie nie geladen wurden. Speichern/Löschen im
 *   Editor lädt sie dort neu und setzt den Veraltet-Merker, so bleibt der Tab aktuell.
 */
class PropertyListViewModel(
    val type: PropertyType,
    private val repository: InseratRepository,
    private val suchprofile: SuchprofilRepository,
    private val veraltet: VeraltetMerker,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(PropertyListUiState())
    val state: StateFlow<PropertyListUiState> = _state.asStateFlow()

    private val _events = Channel<ListEvent>(Channel.BUFFERED)

    /** Einmalige Ereignisse für Snackbars und das Öffnen des Inserats. */
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    /** Nur die letzte Bewertung lässt sich rückgängig machen. */
    private var letzteBewertung: Bewertung? = null
    private var nextBewertungId = 0L

    private lateinit var pager: InseratPager
    private var pagerScope: CoroutineScope? = null

    /** Scrollposition der Wischliste des aktuellen Filters; ein Filterwechsel beginnt oben. */
    private var scroll = ListScroll()

    private val suchprofilePruefen = MutableStateFlow(false)

    init {
        val saved = savedState.get<String>(FILTER_KEY)
        startPager(Filter.entries.firstOrNull { it.name == saved } ?: Filter.Neu)
        beobachteSuchprofile()
    }

    /**
     * „Kein Suchprofil für …“ nur bei leerer Liste. Ist der Speicher der Suchprofile noch leer, holt
     * jedes leer abgeschlossene Laden sie einmal; scheitert das (Netz), bleibt der normale
     * Leerzustand, ohne eigene Fehlermeldung.
     */
    private fun beobachteSuchprofile() {
        viewModelScope.launch {
            _state.map { it.pager.leer && it.pager.loadState == LoadState.Idle }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    if (suchprofile.suchprofile.value == null && !suchprofilePruefen.value) {
                        suchprofilePruefen.value = true
                        launch {
                            suchprofile.laden()
                            suchprofilePruefen.value = false
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(
                _state.map { it.pager.leer }.distinctUntilChanged(),
                suchprofile.suchprofile,
                suchprofilePruefen,
            ) { leer, profile, pruefen ->
                // Profile ohne Typ zählen vorsichtshalber als passend: dann der normale Leerzustand.
                val fehlt = leer && profile != null && profile.none { it.propertyType == type || it.propertyType == null }
                fehlt to (leer && pruefen)
            }.collect { (fehlt, pruefen) ->
                _state.update { it.copy(keinSuchprofilFuerTyp = fehlt, suchprofilePruefen = pruefen) }
            }
        }
    }

    /** Filterwechsel lädt frisch (Entscheidung #10); die alte Liste und ihre Ladevorgänge verfallen. */
    fun selectFilter(filter: Filter) {
        if (filter == _state.value.filter) return
        startPager(filter)
    }

    /** Wo die Wischliste von [filter] beginnt: an der gemerkten Stelle, sonst oben. */
    fun scrollFor(filter: Filter): ListScroll = if (filter == _state.value.filter) scroll else ListScroll()

    /**
     * Merkt die Scrollposition, wenn die Wischliste die Komposition verlässt (Tab-Wechsel, Drehen,
     * Filterwechsel). Gehört sie zu einem alten Filter, wird sie verworfen.
     */
    fun saveScroll(filter: Filter, position: ListScroll) {
        if (filter == _state.value.filter) scroll = position
    }

    /**
     * Läuft, solange der Tab sichtbar ist: Ist sein Typ veraltet (oder wird es), lädt die Liste
     * neu wie bei Pull-to-Refresh, die alte bleibt bis dahin stehen; der Kartenstapel setzt sich
     * dabei zurück (Entscheidung #10). Nicht sichtbare Tabs holen den Merker beim nächsten Besuch ab.
     */
    suspend fun watchStale() {
        veraltet.stale.collect { stale ->
            if (type in stale && veraltet.consume(type)) neuLaden()
        }
    }

    /**
     * Bewertet optimistisch (Entscheidung #6): Verlässt das Inserat den Filter, fliegt die Zeile
     * sofort heraus, sonst (unter „Alle“) wechselt nur das Label. Ein Wisch oder Segment (Detail-Sheet,
     * #14) zum aktuellen Label tut nichts. `unbewertet` heißt „Zurück zu Neu“: Das Backend löscht die
     * Bewertung; den Veraltet-Merker braucht es dafür nicht, „Neu“ lädt beim Filterwechsel frisch (#10).
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
        val bewertung = Bewertung(nextBewertungId++, inserat, label, target, removed)
        letzteBewertung = bewertung
        _events.trySend(ListEvent.Bewertet(label, bewertung.id))
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
        val bewertung = letzteBewertung?.takeIf { it.id == bewertet.bewertungId } ?: return
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
        val url = inseratLink(inserat)
        _events.trySend(if (url == null) ListEvent.NoLink else ListEvent.OpenLink(url))
    }

    /** Pull-to-Refresh: verwirft den Cursor und lädt von oben. */
    fun refresh() = neuLaden()

    /**
     * Liste von oben neu laden (Pull-to-Refresh, Veraltet). Steht „Kein Suchprofil für …“, holt es
     * auch die Suchprofile neu: Das fehlende Profil kann inzwischen im Web angelegt worden sein,
     * oder das Neuladen nach dem Speichern im Editor ist gescheitert.
     */
    private fun neuLaden() {
        if (_state.value.keinSuchprofilFuerTyp) viewModelScope.launch { suchprofile.laden() }
        pager.refresh()
    }

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
        scroll = ListScroll()
        // Die Liste lädt ohnehin frisch; ein schon gesetzter Merker darf nicht ein zweites Mal laden.
        veraltet.consume(type)
        savedState[FILTER_KEY] = filter.name
        _state.update { PropertyListUiState(filter, newPager.state.value, it.keinSuchprofilFuerTyp, it.suchprofilePruefen) }
        scope.launch {
            newPager.state.collect { pagerState ->
                _state.update { it.copy(pager = pagerState) }
                // Kartenstapel: still nachladen, sobald weniger als 5 Karten übrig sind. Der Pager
                // ignoriert das während des Ladens, am Ende und nach einem Fehler (dann „Erneut versuchen“).
                if (filter == Filter.Neu && pagerState.items.size < LOAD_MORE_THRESHOLD) newPager.loadMore()
            }
        }
        newPager.refresh()
    }
}

/** Eine abgeschickte Bewertung, damit Rückgängig und Fehler sie zurückdrehen können. */
private class Bewertung(
    val id: Long,
    val inserat: Inserat,
    val label: Label,
    val pager: InseratPager,
    val removed: RemovedInserat?,
) {
    var job: Job? = null
    var failed = false
}
