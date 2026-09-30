package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
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

/** Kartenstapel im Filter „Neu“: oberste Karte = erster Eintrag der geladenen Liste. */
@OptIn(ExperimentalCoroutinesApi::class)
class KartenstapelViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeInseratRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.stapel(ids: IntRange = 1..8): PropertyListViewModel {
        repository.pages[Label.UNBEWERTET] = page(ids)
        return PropertyListViewModel(PropertyType.HOUSE, repository).also { runCurrent() }
    }

    private val PropertyListViewModel.ids get() = state.value.pager.items.map { it.id }
    private val PropertyListViewModel.top get() = state.value.pager.items.first()

    private fun TestScope.events(vm: PropertyListViewModel): List<ListEvent> {
        val events = mutableListOf<ListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
        return events
    }

    @Test
    fun `Überspringen legt die oberste Karte ans Ende des geladenen Stapels, ohne API-Aufruf`() = runTest(dispatcher) {
        val vm = stapel(1..4)

        vm.skip(vm.top)
        vm.skip(vm.top)

        assertEquals(listOf("3", "4", "1", "2"), vm.ids)
        assertEquals(emptyList<LabelRequest>(), repository.labelRequests)
    }

    @Test
    fun `Wischen nimmt die oberste Karte optimistisch vom Stapel und schickt den PATCH`() = runTest(dispatcher) {
        val vm = stapel(1..8)
        val events = events(vm)

        vm.rate(vm.top, Label.INTERESSANT)
        runCurrent()

        assertEquals(listOf("2", "3", "4", "5", "6", "7", "8"), vm.ids)
        assertEquals(listOf(LabelRequest(PropertyType.HOUSE, "1", Label.INTERESSANT)), repository.labelRequests)
        assertEquals(Label.INTERESSANT, (events.single() as ListEvent.Rated).label)
    }

    @Test
    fun `scheitert der PATCH, kommt die Karte oben auf den Stapel zurück, auch nach Überspringen`() =
        runTest(dispatcher) {
            val vm = stapel(1..8)
            val events = events(vm)
            val gate = CompletableDeferred<Unit>()
            repository.labelGate = gate
            repository.labelResult = networkError

            vm.rate(vm.top, Label.UNINTERESSANT)
            vm.skip(vm.top)
            assertEquals(listOf("3", "4", "5", "6", "7", "8", "2"), vm.ids)

            gate.complete(Unit)
            runCurrent()

            assertEquals(listOf("1", "3", "4", "5", "6", "7", "8", "2"), vm.ids)
            assertEquals(ListEvent.RatingFailed(vm.top, Label.UNINTERESSANT), events.last())
        }

    @Test
    fun `Rückgängig schickt das alte Label und legt die Karte danach oben auf den Stapel`() = runTest(dispatcher) {
        val vm = stapel(1..8)
        val events = events(vm)
        vm.rate(vm.top, Label.INTERESSANT)
        vm.skip(vm.top)
        runCurrent()

        vm.undo(events.last() as ListEvent.Rated)
        runCurrent()

        assertEquals(listOf("1", "3", "4", "5", "6", "7", "8", "2"), vm.ids)
        assertEquals(LabelRequest(PropertyType.HOUSE, "1", Label.UNBEWERTET), repository.labelRequests.last())
    }

    @Test
    fun `lädt still nach, sobald weniger als 5 Karten übrig sind`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..20)
        repository.nextPages[PageCursor("t20", "20")] = page(21..25)
        val vm = PropertyListViewModel(PropertyType.HOUSE, repository).also { runCurrent() }
        repeat(15) { vm.rate(vm.top, Label.UNINTERESSANT) }
        runCurrent()
        assertEquals(1, repository.pageRequests.size)

        vm.rate(vm.top, Label.UNINTERESSANT)
        runCurrent()

        assertEquals(PageCursor("t20", "20"), repository.pageRequests.last().cursor)
        assertEquals(listOf("17", "18", "19", "20", "21", "22", "23", "24", "25"), vm.ids)
        assertEquals(true, vm.state.value.pager.endReached)
    }

    @Test
    fun `Alle als uninteressant markieren archiviert die Neu-Liste des Typs, meldet die Anzahl und lädt neu`() =
        runTest(dispatcher) {
            val vm = stapel(1..8)
            val events = events(vm)
            repository.bulkResult = ApiResult.Success(37)
            repository.pages[Label.UNBEWERTET] = page(9..10)

            vm.archiveAllNew()
            runCurrent()

            assertEquals(listOf(BulkRequest(PropertyType.HOUSE, Label.UNINTERESSANT)), repository.bulkRequests)
            assertEquals(ListEvent.BulkArchived(37), events.single())
            assertEquals(listOf("9", "10"), vm.ids)
        }

    @Test
    fun `scheitert Alle als uninteressant markieren, bleibt der Stapel und die App meldet es`() = runTest(dispatcher) {
        val vm = stapel(1..8)
        val events = events(vm)
        repository.bulkResult = networkError

        vm.archiveAllNew()
        runCurrent()

        assertEquals(ListEvent.BulkArchiveFailed, events.single())
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8"), vm.ids)
        assertEquals(1, repository.pageRequests.size)
    }
}
