package de.immoscrabber.app.properties

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.immoscrabber.app.R
import de.immoscrabber.app.core.data.InseratRepository
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiError
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Nachladen, sobald weniger als so viele Einträge unterhalb des sichtbaren Bereichs liegen. */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * Tab eines Immobilientyps. Das ViewModel hängt am Back-Stack-Eintrag des Tabs; Filter und
 * Liste überleben so Tab-Wechsel und Drehen.
 */
@Composable
fun PropertyTab(type: PropertyType, repository: InseratRepository) {
    val viewModel: PropertyListViewModel = viewModel(
        key = "properties-${type.apiValue}",
        factory = viewModelFactory { initializer { PropertyListViewModel(type, repository) } },
    )
    PropertyListScreen(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyListScreen(viewModel: PropertyListViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val typeName = stringResource(viewModel.type.pluralName)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val texts = SnackbarTexts(
        ratedFavorite = stringResource(R.string.rated_favorite),
        ratedArchive = stringResource(R.string.rated_archive),
        undo = stringResource(R.string.undo),
        ratingFailed = stringResource(R.string.rating_failed),
        retry = stringResource(R.string.retry),
        undoFailed = stringResource(R.string.undo_failed),
        noLink = stringResource(R.string.no_link),
        linkFailed = stringResource(R.string.link_failed),
        archiveAllFailed = stringResource(R.string.archive_all_new_failed, typeName),
    )
    var confirmArchiveAll by rememberSaveable { mutableStateOf(false) }

    // collectLatest: jede neue Snackbar ersetzt die vorige, also bleibt nur die letzte
    // Bewertung rückgängig zu machen.
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            handleEvent(event, viewModel, snackbarHostState, texts, context)
        }
    }

    val loadFailedText = stringResource(R.string.load_failed, typeName)
    val loadState = state.pager.loadState
    LaunchedEffect(loadState) {
        // Pull-to-Refresh gescheitert, die alte Liste steht noch: nur kurz melden.
        if (loadState is LoadState.Failed && loadState.kind == LoadKind.Refresh && state.pager.loaded) {
            snackbarHostState.showSnackbar(loadFailedText)
        }
    }

    // Über dem Kartenstapel sitzt die Snackbar oberhalb von „Überspringen“ (Entscheidung #3), im
    // Platz, den der Stapel unter der Karte freihält (#52).
    val stackVisible = state.filter == Filter.Neu && state.pager.items.isNotEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(typeName) },
                actions = { if (state.filter == Filter.Neu) NeuOverflowMenu(onArchiveAll = { confirmArchiveAll = true }) },
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                Modifier.padding(bottom = if (stackVisible) StackFooterHeight else 0.dp),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            FilterChips(state.filter, viewModel::selectFilter)
            // Scrollposition je Filter: ein Filterwechsel beginnt oben.
            val listState = rememberSaveable(state.filter, saver = LazyListState.Saver) { LazyListState() }
            PullToRefreshBox(
                isRefreshing = state.pager.loaded && loadState == LoadState.Loading(LoadKind.Refresh),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ListContent(state, typeName, listState, viewModel)
            }
        }
    }

    if (confirmArchiveAll) {
        ArchiveAllDialog(
            typeName = typeName,
            onConfirm = {
                confirmArchiveAll = false
                viewModel.archiveAllNew()
            },
            onDismiss = { confirmArchiveAll = false },
        )
    }
}

/** ⋮-Menü in der App-Leiste, nur unter „Neu“ (Entscheidung #6). */
@Composable
private fun NeuOverflowMenu(onArchiveAll: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_all_new)) },
                onClick = {
                    expanded = false
                    onArchiveAll()
                },
            )
        }
    }
}

/** Bestätigung ohne Zahl (Entscheidung #6); der Endpunkt lässt sich nicht rückgängig machen. */
@Composable
private fun ArchiveAllDialog(typeName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.archive_all_new_question, typeName)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.archive_all_new_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ListContent(
    state: PropertyListUiState,
    typeName: String,
    listState: LazyListState,
    viewModel: PropertyListViewModel,
) {
    val pager = state.pager
    val loadState = pager.loadState
    when {
        !pager.loaded && loadState is LoadState.Failed ->
            if (loadState.error is ApiError.BadRequest) {
                // 400 nur ohne jedes Suchprofil; den Button zum Editor bringt dessen Ticket.
                MessageState(
                    title = stringResource(R.string.no_search_profile_title),
                    text = stringResource(R.string.no_search_profile_text),
                )
            } else {
                MessageState(
                    title = stringResource(R.string.load_failed, typeName),
                    action = stringResource(R.string.retry) to viewModel::retryLoading,
                )
            }
        !pager.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // Kartenstapel leer, aber es gibt weitere Seiten: Nachladen läuft oder ist gescheitert.
        state.filter == Filter.Neu && pager.items.isEmpty() && !pager.endReached ->
            if (loadState is LoadState.Failed) {
                MessageState(
                    title = stringResource(R.string.load_failed, typeName),
                    action = stringResource(R.string.retry) to viewModel::retryLoading,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        pager.items.isEmpty() -> EmptyState(state.filter, typeName)
        state.filter == Filter.Neu -> Kartenstapel(
            items = pager.items,
            moreAvailable = !pager.endReached,
            onRate = viewModel::rate,
            onSkip = viewModel::skip,
            onOpen = viewModel::open,
        )
        else -> Wischliste(
            items = pager.items,
            filter = state.filter,
            loadState = loadState,
            listState = listState,
            onRate = viewModel::rate,
            onOpen = viewModel::open,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::retryLoading,
        )
    }
}

@Composable
private fun FilterChips(selected: Filter, onSelect: (Filter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.title)) },
            )
        }
    }
}

private val Filter.title: Int
    get() = when (this) {
        Filter.Neu -> R.string.filter_new
        Filter.Favoriten -> R.string.filter_favorites
        Filter.Alle -> R.string.filter_all
        Filter.Archiv -> R.string.filter_archive
    }

@Composable
private fun Wischliste(
    items: List<Inserat>,
    filter: Filter,
    loadState: LoadState,
    listState: LazyListState,
    onRate: (Inserat, Label) -> Unit,
    onOpen: (Inserat) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    // Neu gestartet, sobald die Liste wächst; so löst eine kurze Liste nicht endlos aus.
    LaunchedEffect(listState, items.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= items.size - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { inserat ->
            Column(Modifier.animateItem()) {
                SwipeableInseratRow(
                    inserat = inserat,
                    filter = filter,
                    onRate = { label -> onRate(inserat, label) },
                    onClick = { onOpen(inserat) },
                )
                HorizontalDivider()
            }
        }
        item(key = "footer") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    loadState == LoadState.Loading(LoadKind.Append) ->
                        CircularProgressIndicator(Modifier.size(32.dp))
                    loadState is LoadState.Failed && loadState.kind == LoadKind.Append ->
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filter: Filter, typeName: String) = when (filter) {
    Filter.Neu -> MessageState(
        title = stringResource(R.string.empty_new_title),
        text = stringResource(R.string.empty_new_text, typeName),
        showCheck = true,
    )
    Filter.Favoriten -> MessageState(title = stringResource(R.string.empty_favorites))
    Filter.Alle -> MessageState(title = stringResource(R.string.empty_all, typeName))
    Filter.Archiv -> MessageState(title = stringResource(R.string.empty_archive))
}

/** Leer-, Fehler- und Hinweiszustand; scrollbar, damit Pull-to-Refresh auch hier greift. */
@Composable
private fun MessageState(
    title: String,
    text: String? = null,
    showCheck: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showCheck) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        text?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
    }
}

private class SnackbarTexts(
    val ratedFavorite: String,
    val ratedArchive: String,
    val undo: String,
    val ratingFailed: String,
    val retry: String,
    val undoFailed: String,
    val noLink: String,
    val linkFailed: String,
    val archiveAllFailed: String,
)

private suspend fun handleEvent(
    event: ListEvent,
    viewModel: PropertyListViewModel,
    snackbarHostState: SnackbarHostState,
    texts: SnackbarTexts,
    context: Context,
) {
    when (event) {
        is ListEvent.Rated -> {
            val message = if (event.label == Label.INTERESSANT) texts.ratedFavorite else texts.ratedArchive
            val result = snackbarHostState.showSnackbar(message, texts.undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
        }
        is ListEvent.RatingFailed -> {
            val result = snackbarHostState.showSnackbar(texts.ratingFailed, texts.retry, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.retry(event)
        }
        ListEvent.UndoFailed -> snackbarHostState.showSnackbar(texts.undoFailed)
        ListEvent.NoLink -> snackbarHostState.showSnackbar(texts.noLink)
        is ListEvent.BulkArchived -> snackbarHostState.showSnackbar(
            context.resources.getQuantityString(viewModel.type.archivedAllMessage, event.count, event.count),
        )
        ListEvent.BulkArchiveFailed -> snackbarHostState.showSnackbar(texts.archiveAllFailed)
        is ListEvent.OpenLink -> {
            try {
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, event.url.toUri())
            } catch (_: ActivityNotFoundException) {
                snackbarHostState.showSnackbar(texts.linkFailed)
            }
        }
    }
}
