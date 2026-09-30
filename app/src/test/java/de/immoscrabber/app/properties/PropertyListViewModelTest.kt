package de.immoscrabber.app.properties

import de.immoscrabber.app.core.data.InseratRepository
import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyListViewModelTest {
    /** Wie `Dispatchers.Main.immediate` des `viewModelScope`: Zustandsänderungen kommen sofort an. */
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeInseratRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): PropertyListViewModel =
        PropertyListViewModel(PropertyType.HOUSE, repository).also { runCurrent() }

    private val PropertyListViewModel.ids get() = state.value.pager.items.map { it.id }

    @Test
    fun `startet im Filter Neu und lädt die unbewerteten Häuser`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(ids = 1..3)

        val vm = viewModel()

        assertEquals(Filter.Neu, vm.state.value.filter)
        assertEquals(listOf("1", "2", "3"), vm.ids)
        assertEquals(listOf(PageRequest(PropertyType.HOUSE, Label.UNBEWERTET, null)), repository.pageRequests)
    }

    @Test
    fun `Filterwechsel lädt frisch mit dem Label des Filters`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(ids = 1..3)
        repository.pages[null] = page(ids = 10..11)
        val vm = viewModel()

        vm.selectFilter(Filter.Alle)
        runCurrent()

        assertEquals(Filter.Alle, vm.state.value.filter)
        assertEquals(listOf("10", "11"), vm.ids)
        assertEquals(PageRequest(PropertyType.HOUSE, null, null), repository.pageRequests.last())
    }

    private fun TestScope.events(vm: PropertyListViewModel): List<ListEvent> {
        val events = mutableListOf<ListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    private fun TestScope.favoritenViewModel(): PropertyListViewModel {
        repository.pages[Label.UNBEWERTET] = page(ids = 1..3)
        repository.pages[Label.INTERESSANT] = page(ids = 1..3, label = Label.INTERESSANT)
        return viewModel().also {
            it.selectFilter(Filter.Favoriten)
            runCurrent()
        }
    }

    @Test
    fun `Bewerten in Favoriten nimmt die Zeile heraus, schickt den PATCH und bietet Rückgängig an`() = runTest(dispatcher) {
        val vm = favoritenViewModel()
        val events = events(vm)

        vm.bewerten(vm.state.value.pager.items[1], Label.UNINTERESSANT)

        assertEquals(listOf("1", "3"), vm.ids)
        runCurrent()
        assertEquals(listOf(LabelRequest(PropertyType.HOUSE, "2", Label.UNINTERESSANT)), repository.labelRequests)
        assertEquals(Label.UNINTERESSANT, (events.single() as ListEvent.Bewertet).label)
    }

    @Test
    fun `Bewerten unter Alle lässt die Zeile stehen und wechselt nur das Label`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(ids = 1..3)
        repository.pages[null] = page(ids = 1..3)
        val vm = viewModel()
        vm.selectFilter(Filter.Alle)
        runCurrent()

        vm.bewerten(vm.state.value.pager.items[0], Label.INTERESSANT)
        runCurrent()

        assertEquals(listOf("1", "2", "3"), vm.ids)
        assertEquals(Label.INTERESSANT, vm.state.value.pager.items[0].label)
        assertEquals(listOf(LabelRequest(PropertyType.HOUSE, "1", Label.INTERESSANT)), repository.labelRequests)
    }

    @Test
    fun `Wisch zum aktuellen Label ändert nichts und ruft die API nicht`() = runTest(dispatcher) {
        val vm = favoritenViewModel()
        val events = events(vm)

        vm.bewerten(vm.state.value.pager.items[0], Label.INTERESSANT)
        runCurrent()

        assertEquals(listOf("1", "2", "3"), vm.ids)
        assertEquals(emptyList<LabelRequest>(), repository.labelRequests)
        assertEquals(emptyList<ListEvent>(), events)
    }

    @Test
    fun `scheitert der PATCH, kommt die Zeile an ihre alte Stelle zurück, und Erneut versuchen bewertet wieder`() =
        runTest(dispatcher) {
            val vm = favoritenViewModel()
            val events = events(vm)
            repository.labelResult = networkError

            val zwei = vm.state.value.pager.items[1]
            vm.bewerten(zwei, Label.UNINTERESSANT)
            runCurrent()

            assertEquals(listOf("1", "2", "3"), vm.ids)
            val failed = events.last() as ListEvent.BewertungFehlgeschlagen

            repository.labelResult = ApiResult.Success(Unit)
            vm.retry(failed)
            runCurrent()

            assertEquals(listOf("1", "3"), vm.ids)
            assertEquals(2, repository.labelRequests.count { it == LabelRequest(PropertyType.HOUSE, "2", Label.UNINTERESSANT) })
        }

    @Test
    fun `Rückgängig setzt das alte Label per PATCH und holt die Zeile an ihre Stelle zurück`() = runTest(dispatcher) {
        val vm = favoritenViewModel()
        val events = events(vm)
        vm.bewerten(vm.state.value.pager.items[1], Label.UNINTERESSANT)
        runCurrent()

        vm.undo(events.last() as ListEvent.Bewertet)
        runCurrent()

        assertEquals(listOf("1", "2", "3"), vm.ids)
        assertEquals(LabelRequest(PropertyType.HOUSE, "2", Label.INTERESSANT), repository.labelRequests.last())
    }

    @Test
    fun `Rückgängig unter Alle stellt das alte Label wieder her`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(ids = 1..3)
        repository.pages[null] = page(ids = 1..3)
        val vm = viewModel()
        vm.selectFilter(Filter.Alle)
        val events = events(vm)
        vm.bewerten(vm.state.value.pager.items[0], Label.INTERESSANT)

        vm.undo(events.last() as ListEvent.Bewertet)
        runCurrent()

        assertEquals(Label.UNBEWERTET, vm.state.value.pager.items[0].label)
        assertEquals(LabelRequest(PropertyType.HOUSE, "1", Label.UNBEWERTET), repository.labelRequests.last())
    }

    @Test
    fun `nur die letzte Bewertung lässt sich rückgängig machen`() = runTest(dispatcher) {
        val vm = favoritenViewModel()
        val events = events(vm)
        vm.bewerten(vm.state.value.pager.items[0], Label.UNINTERESSANT)
        val first = events.last() as ListEvent.Bewertet
        vm.bewerten(vm.state.value.pager.items[0], Label.UNINTERESSANT)
        runCurrent()

        vm.undo(first)
        runCurrent()

        assertEquals(listOf("3"), vm.ids)
        assertEquals(2, repository.labelRequests.size)
    }

    @Test
    fun `scheitert Rückgängig, bleibt das Inserat bewertet und die App meldet es`() = runTest(dispatcher) {
        val vm = favoritenViewModel()
        val events = events(vm)
        vm.bewerten(vm.state.value.pager.items[1], Label.UNINTERESSANT)
        runCurrent()
        repository.labelResult = networkError

        vm.undo(events.last() as ListEvent.Bewertet)
        runCurrent()

        assertEquals(listOf("1", "3"), vm.ids)
        assertEquals(ListEvent.UndoFailed, events.last())
    }

    @Test
    fun `Tipp öffnet den Link, ohne URL kommt der Hinweis`() = runTest(dispatcher) {
        val vm = favoritenViewModel()
        val events = events(vm)

        vm.open(inserat("1", url = "https://example.com/haus/1"))
        vm.open(inserat("2", url = " "))

        assertEquals(listOf(ListEvent.OpenLink("https://example.com/haus/1"), ListEvent.NoLink), events)
    }
}

data class PageRequest(val type: PropertyType, val label: Label?, val cursor: PageCursor?)
data class LabelRequest(val type: PropertyType, val id: String, val label: Label)
data class BulkRequest(val type: PropertyType, val label: Label)

class FakeInseratRepository : InseratRepository {
    /** Erste Seiten je Label (`null` = „Alle“). */
    val pages = mutableMapOf<Label?, ApiResult<InseratPage>>()
    val pageRequests = mutableListOf<PageRequest>()
    val labelRequests = mutableListOf<LabelRequest>()
    var labelResult: ApiResult<Unit> = ApiResult.Success(Unit)

    /** Hält PATCH-Antworten zurück, bis es abgeschlossen wird. */
    var labelGate: CompletableDeferred<Unit>? = null

    /** Folgeseiten je Cursor (die erste Seite kommt aus [pages]). */
    val nextPages = mutableMapOf<PageCursor, ApiResult<InseratPage>>()

    override suspend fun seite(type: PropertyType, label: Label?, cursor: PageCursor?): ApiResult<InseratPage> {
        pageRequests += PageRequest(type, label, cursor)
        if (cursor != null) return nextPages[cursor] ?: error("keine Seite für $cursor")
        return pages[label] ?: error("keine Seite für $label")
    }

    val bulkRequests = mutableListOf<BulkRequest>()
    var bulkResult: ApiResult<Int> = ApiResult.Success(0)

    override suspend fun alleNeuenBewerten(type: PropertyType, label: Label): ApiResult<Int> {
        bulkRequests += BulkRequest(type, label)
        return bulkResult
    }

    override suspend fun bewerten(type: PropertyType, id: String, label: Label): ApiResult<Unit> {
        labelRequests += LabelRequest(type, id, label)
        labelGate?.await()
        return labelResult
    }
}
