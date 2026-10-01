package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Long Press → „Zurück zu Neu“ in der Wischliste (#14). */
@OptIn(ExperimentalCoroutinesApi::class)
class ZurueckZuNeuTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeInseratRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val PropertyListViewModel.ids get() = state.value.pager.items.map { it.id }

    private fun TestScope.viewModelIn(filter: Filter): PropertyListViewModel {
        repository.pages[Label.UNBEWERTET] = page(ids = 7..7)
        repository.pages[Label.INTERESSANT] = page(ids = 1..3, label = Label.INTERESSANT)
        repository.pages[Label.UNINTERESSANT] = page(ids = 1..3, label = Label.UNINTERESSANT)
        return testViewModel(repository).also {
            runCurrent()
            it.selectFilter(filter)
            runCurrent()
        }
    }

    private fun TestScope.events(vm: PropertyListViewModel): List<ListEvent> {
        val events = mutableListOf<ListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    @Test
    fun `aus Favoriten verlässt die Zeile die Liste und der PATCH setzt unbewertet`() = runTest(dispatcher) {
        val vm = viewModelIn(Filter.Favoriten)
        val events = events(vm)

        vm.zurueckZuNeu(vm.state.value.pager.items[1])
        runCurrent()

        assertEquals(listOf("1", "3"), vm.ids)
        assertEquals(listOf(LabelRequest(PropertyType.HOUSE, "2", Label.UNBEWERTET)), repository.labelRequests)
        assertEquals(Label.UNBEWERTET, (events.single() as ListEvent.Bewertet).label)
    }

    @Test
    fun `aus dem Archiv verlässt die Zeile die Liste`() = runTest(dispatcher) {
        val vm = viewModelIn(Filter.Archiv)

        vm.zurueckZuNeu(vm.state.value.pager.items[0])
        runCurrent()

        assertEquals(listOf("2", "3"), vm.ids)
        assertEquals(listOf(LabelRequest(PropertyType.HOUSE, "1", Label.UNBEWERTET)), repository.labelRequests)
    }

    @Test
    fun `unter Alle bleibt die Zeile stehen, nur das Label wird unbewertet`() = runTest(dispatcher) {
        repository.pages[null] = ApiResult.Success(
            InseratPage(listOf(inserat("1", Label.INTERESSANT), inserat("2", Label.UNINTERESSANT)), null),
        )
        val vm = viewModelIn(Filter.Alle)

        vm.zurueckZuNeu(vm.state.value.pager.items[1])
        runCurrent()

        assertEquals(listOf("1", "2"), vm.ids)
        assertEquals(listOf(Label.INTERESSANT, Label.UNBEWERTET), vm.state.value.pager.items.map { it.label })
        assertEquals(listOf(LabelRequest(PropertyType.HOUSE, "2", Label.UNBEWERTET)), repository.labelRequests)
    }

    @Test
    fun `ein unbewertetes Inserat bleibt unberührt`() = runTest(dispatcher) {
        repository.pages[null] = page(ids = 1..2)
        val vm = viewModelIn(Filter.Alle)
        val events = events(vm)

        vm.zurueckZuNeu(vm.state.value.pager.items[0])
        runCurrent()

        assertEquals(emptyList<LabelRequest>(), repository.labelRequests)
        assertEquals(emptyList<ListEvent>(), events)
    }

    @Test
    fun `Rückgängig setzt das alte Label per PATCH und holt die Zeile an ihre Stelle zurück`() = runTest(dispatcher) {
        val vm = viewModelIn(Filter.Archiv)
        val events = events(vm)
        vm.zurueckZuNeu(vm.state.value.pager.items[1])
        runCurrent()

        vm.undo(events.last() as ListEvent.Bewertet)
        runCurrent()

        assertEquals(listOf("1", "2", "3"), vm.ids)
        assertEquals(Label.UNINTERESSANT, vm.state.value.pager.items[1].label)
        assertEquals(LabelRequest(PropertyType.HOUSE, "2", Label.UNINTERESSANT), repository.labelRequests.last())
    }

    @Test
    fun `scheitert der PATCH, kommt die Zeile zurück, und Erneut versuchen setzt wieder unbewertet`() = runTest(dispatcher) {
        val vm = viewModelIn(Filter.Favoriten)
        val events = events(vm)
        repository.labelResult = networkError

        vm.zurueckZuNeu(vm.state.value.pager.items[1])
        runCurrent()

        assertEquals(listOf("1", "2", "3"), vm.ids)
        val failed = events.last() as ListEvent.BewertungFehlgeschlagen
        assertEquals(Label.UNBEWERTET, failed.label)

        repository.labelResult = ApiResult.Success(Unit)
        vm.retry(failed)
        runCurrent()

        assertEquals(listOf("1", "3"), vm.ids)
        assertEquals(LabelRequest(PropertyType.HOUSE, "2", Label.UNBEWERTET), repository.labelRequests.last())
    }

    @Test
    fun `danach lädt Neu beim Filterwechsel frisch, die Wischliste lädt nicht neu`() = runTest(dispatcher) {
        val vm = viewModelIn(Filter.Favoriten)
        val requestsBefore = repository.pageRequests.size

        vm.zurueckZuNeu(vm.state.value.pager.items[1])
        runCurrent()
        assertEquals(requestsBefore, repository.pageRequests.size)

        repository.pages[Label.UNBEWERTET] = page(ids = 2..2)
        vm.selectFilter(Filter.Neu)
        runCurrent()

        assertEquals(listOf("2"), vm.ids)
        assertEquals(PageRequest(PropertyType.HOUSE, Label.UNBEWERTET, null), repository.pageRequests.last())
    }
}
