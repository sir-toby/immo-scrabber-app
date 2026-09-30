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
        _state.update { it.copy(loadState = LoadState.Loading(kind)) }
        job = scope.launch {
            val result = loadPage(cursor)
            when (result) {
                is ApiResult.Success -> _state.update {
                    val page = result.value
                    val items = if (kind == LoadKind.Refresh) page.inserate else it.items + page.inserate
                    PagerState(items, page.nextCursor, page.nextCursor == null, LoadState.Idle, loaded = true)
                }
                is ApiResult.Failure -> _state.update { it.copy(loadState = LoadState.Failed(kind, result.error)) }
            }
        }
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

    /** Ersetzt das Inserat [id] an seiner Stelle (etwa ein neues Label unter „Alle“). */
    fun update(id: String, transform: (Inserat) -> Inserat) {
        _state.update { current ->
            current.copy(items = current.items.map { if (it.id == id) transform(it) else it })
        }
    }
}
