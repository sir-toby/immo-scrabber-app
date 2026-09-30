package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Erste Seite (neu) oder Folgeseite (anhängen). */
enum class LoadKind { Refresh, Append }

/** Was der Pager gerade lädt oder woran er gescheitert ist. */
sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val kind: LoadKind) : LoadState

    /** Letzter Ladeversuch gescheitert; die geladenen Einträge bleiben. */
    data class Failed(val kind: LoadKind, val error: ApiError) : LoadState
}

/** Zustand einer Liste: geladene Einträge, Cursor für die nächste Seite, Ende, Ladestatus. */
data class PagerState(
    val items: List<Inserat> = emptyList(),
    val cursor: PageCursor? = null,
    val endReached: Boolean = false,
    val loadState: LoadState = LoadState.Idle,
    /** Mindestens eine Seite ist angekommen (davor zeigt die UI den Spinner in der Mitte). */
    val loaded: Boolean = false,
)

/** Ein optimistisch entferntes Inserat samt seiner Stelle, für [InseratPager.restore]. */
data class RemovedInserat(val inserat: Inserat, val index: Int)

/**
 * Eigener kleiner Pager (Entscheidung #10) für eine Liste Inserate, geteilt von Wischliste und
 * Kartenstapel; nur die Nachladeschwelle legt die UI fest.
 *
 * - Seiten kommen über [loadPage]; der Cursor steckt im [InseratPage.nextCursor], eine Seite
 *   unter der Seitengröße (kein Cursor) bedeutet Ende.
 * - [refresh] verwirft den Cursor und bricht laufendes Nachladen ab; die alten Einträge
 *   bleiben stehen, bis die neue erste Seite da ist.
 * - [loadMore] ist für den automatischen Auslöser beim Scrollen gedacht und tut nichts, solange
 *   geladen wird, das Ende erreicht ist oder der letzte Versuch scheiterte; dafür gibt es [retry].
 * - Keine ID steht je doppelt in der Liste (Schlüssel in LazyColumn und Kartenstapel), auch wenn
 *   Seiten sich überlappen; eine Folgeseite ohne neue Einträge gilt als Ende.
 * - [remove]/[restore]/[update] ändern die geladene Liste optimistisch (Bewerten, Rückgängig),
 *   ohne den Cursor zu berühren.
 */
class InseratPager(
    private val scope: CoroutineScope,
    private val loadPage: suspend (cursor: PageCursor?) -> ApiResult<InseratPage>,
) {
    private val _state = MutableStateFlow(PagerState())
    val state: StateFlow<PagerState> = _state.asStateFlow()

    private var job: Job? = null

    /** Zählt Ladevorgänge hoch; nur die Antwort des jüngsten wird übernommen. */
    private var generation = 0

    fun refresh() {
        if (_state.value.loadState == LoadState.Loading(LoadKind.Refresh)) return
        load(LoadKind.Refresh, cursor = null)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.loaded || current.endReached || current.loadState != LoadState.Idle) return
        load(LoadKind.Append, current.cursor)
    }

    /** Wiederholt den gescheiterten Ladeversuch (erste Seite oder Folgeseite). */
    fun retry() {
        val failed = _state.value.loadState as? LoadState.Failed ?: return
        when (failed.kind) {
            LoadKind.Refresh -> load(LoadKind.Refresh, cursor = null)
            LoadKind.Append -> load(LoadKind.Append, _state.value.cursor)
        }
    }

    private fun load(kind: LoadKind, cursor: PageCursor?) {
        job?.cancel()
        // Nur die Antwort des jüngsten Ladevorgangs zählt; eine ältere, die trotz Abbruch
        // noch ankommt (etwa ein Nachladen nach einem Refresh), wird verworfen.
        val generation = ++generation
        _state.update { it.copy(loadState = LoadState.Loading(kind)) }
        job = scope.launch {
            val result = loadPage(cursor)
            if (generation != this@InseratPager.generation) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.withPage(kind, result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loadState = LoadState.Failed(kind, result.error)) }
            }
        }
    }

    private fun PagerState.withPage(kind: LoadKind, page: InseratPage): PagerState {
        val base = if (kind == LoadKind.Refresh) emptyList() else items
        val merged = base.appendNew(page.inserate)
        // Bringt eine Folgeseite nichts Neues, rückt der Cursor nicht vor: Ende statt endlos nachladen.
        val stalled = kind == LoadKind.Append && merged.size == items.size
        val end = page.nextCursor == null || stalled
        return PagerState(merged, page.nextCursor.takeUnless { end }, end, LoadState.Idle, loaded = true)
    }

    /** Nimmt das Inserat [id] aus der Liste; `null`, wenn es nicht geladen ist. */
    fun remove(id: String): RemovedInserat? {
        var removed: RemovedInserat? = null
        _state.update { current ->
            val index = current.items.indexOfFirst { it.id == id }
            if (index < 0) {
                removed = null
                return@update current
            }
            removed = RemovedInserat(current.items[index], index)
            current.copy(items = current.items.toMutableList().apply { removeAt(index) })
        }
        return removed
    }

    /** Setzt ein entferntes Inserat an seine alte Stelle (höchstens ans Ende); nie doppelt. */
    fun restore(removed: RemovedInserat) {
        _state.update { current ->
            if (current.items.any { it.id == removed.inserat.id }) return@update current
            val index = removed.index.coerceAtMost(current.items.size)
            current.copy(items = current.items.toMutableList().apply { add(index, removed.inserat) })
        }
    }

    /** Stellt das Inserat [id] ans Ende der geladenen Liste (Überspringen im Kartenstapel). */
    fun moveToEnd(id: String) {
        _state.update { current ->
            val index = current.items.indexOfFirst { it.id == id }
            if (index < 0) return@update current
            current.copy(items = current.items.toMutableList().apply { add(removeAt(index)) })
        }
    }

    /**
     * Hängt nur Inserate an, die weder in der Liste noch weiter vorn auf der Seite stehen. Keine
     * ID darf doppelt vorkommen (sie ist Schlüssel in LazyColumn und Kartenstapel); Seiten können
     * sich aber überlappen, etwa wenn der Server-Cursor bei gleichen `created_at` nicht vorrückt.
     */
    private fun List<Inserat>.appendNew(page: List<Inserat>): List<Inserat> {
        val known = mapTo(HashSet()) { it.id }
        return this + page.filter { known.add(it.id) }
    }

    /** Ersetzt das Inserat [id] an seiner Stelle (etwa ein neues Label unter „Alle“). */
    fun update(id: String, transform: (Inserat) -> Inserat) {
        _state.update { current ->
            current.copy(items = current.items.map { if (it.id == id) transform(it) else it })
        }
    }
}
