package de.immoscrabber.app.properties

import androidx.lifecycle.SavedStateHandle
import de.immoscrabber.app.core.data.VeraltetMerker
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration

/** Zustand eines Tabs über Tab-Wechsel, Beenden durch das System und den Veraltet-Merker (#29). */
@OptIn(ExperimentalCoroutinesApi::class)
class PropertyListZustandTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeInseratRepository()
    private val veraltet = VeraltetMerker(now = { Duration.ZERO }, threshold = Duration.INFINITE)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        testViewModel(repository, veraltet, savedState).also { runCurrent() }

    private val PropertyListViewModel.ids get() = state.value.pager.items.map { it.id }

    /** Der Tab ist sichtbar, solange der zurückgegebene Job läuft. */
    private fun TestScope.sichtbar(vm: PropertyListViewModel): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.watchStale() }

    // --- Filter je Tab (SavedStateHandle) ---

    @Test
    fun `der Filter landet im gespeicherten Zustand`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)
        repository.pages[Label.UNINTERESSANT] = page(4..5, Label.UNINTERESSANT)
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState)

        vm.selectFilter(Filter.Archiv)

        assertEquals(Filter.Archiv.name, savedState.get<String>(FILTER_KEY))
    }

    @Test
    fun `nach dem Beenden durch das System startet der Tab im gespeicherten Filter und lädt von oben`() =
        runTest(dispatcher) {
            repository.pages[Label.UNINTERESSANT] = page(4..5, Label.UNINTERESSANT)

            val vm = viewModel(SavedStateHandle(mapOf(FILTER_KEY to Filter.Archiv.name)))

            assertEquals(Filter.Archiv, vm.state.value.filter)
            assertEquals(listOf(PageRequest(PropertyType.HOUSE, Label.UNINTERESSANT, null)), repository.pageRequests)
            assertEquals(ListScroll(), vm.scrollFor(Filter.Archiv))
        }

    @Test
    fun `ein unbekannter gespeicherter Filter fällt auf Neu zurück`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)

        val vm = viewModel(SavedStateHandle(mapOf(FILTER_KEY to "Quatsch")))

        assertEquals(Filter.Neu, vm.state.value.filter)
    }

    // --- Scrollposition je Tab (nur im ViewModel, also nicht über das Beenden hinaus) ---

    @Test
    fun `die Scrollposition bleibt im ViewModel, bis der Filter wechselt`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)
        repository.pages[null] = page(1..30)
        repository.pages[Label.INTERESSANT] = page(1..3, Label.INTERESSANT)
        val vm = viewModel()
        vm.selectFilter(Filter.Alle)

        vm.saveScroll(Filter.Alle, ListScroll(12, 40))
        assertEquals(ListScroll(12, 40), vm.scrollFor(Filter.Alle))

        vm.selectFilter(Filter.Favoriten)
        assertEquals(ListScroll(), vm.scrollFor(Filter.Favoriten))
    }

    @Test
    fun `die Position der alten Liste nach dem Filterwechsel wird verworfen`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)
        repository.pages[null] = page(1..30)
        repository.pages[Label.INTERESSANT] = page(1..3, Label.INTERESSANT)
        val vm = viewModel()
        vm.selectFilter(Filter.Alle)
        vm.selectFilter(Filter.Favoriten)

        // Die alte Liste meldet ihre Position erst beim Verlassen der Komposition.
        vm.saveScroll(Filter.Alle, ListScroll(12, 40))

        assertEquals(ListScroll(), vm.scrollFor(Filter.Favoriten))
    }

    // --- Veraltet-Merker ---

    @Test
    fun `ist der sichtbare Tab veraltet, lädt er sofort neu, und die alte Liste bleibt stehen`() =
        runTest(dispatcher) {
            repository.pages[Label.UNBEWERTET] = page(1..3)
            val vm = viewModel()
            sichtbar(vm)
            val gate = CompletableDeferred<Unit>()
            repository.pageGate = gate
            repository.pages[Label.UNBEWERTET] = page(5..7)

            veraltet.markStale(PropertyType.HOUSE)

            assertEquals(2, repository.pageRequests.size)
            assertEquals(LoadState.Loading(LoadKind.Refresh), vm.state.value.pager.loadState)
            assertEquals(listOf("1", "2", "3"), vm.ids)
            assertFalse(veraltet.consume(PropertyType.HOUSE))

            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("5", "6", "7"), vm.ids)
        }

    @Test
    fun `der Merker eines anderen Typs lässt den Tab in Ruhe`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)
        val vm = viewModel()
        sichtbar(vm)

        veraltet.markStale(PropertyType.FLAT)

        assertEquals(1, repository.pageRequests.size)
        assertTrue(veraltet.consume(PropertyType.FLAT))
    }

    @Test
    fun `ein nicht sichtbarer Tab lädt erst beim nächsten Besuch neu`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)
        val vm = viewModel()
        sichtbar(vm).cancel()

        veraltet.markStale(PropertyType.HOUSE)
        assertEquals(1, repository.pageRequests.size)

        sichtbar(vm)
        assertEquals(2, repository.pageRequests.size)
        assertFalse(veraltet.consume(PropertyType.HOUSE))
    }

    @Test
    fun `ein neu angelegter Tab, der schon veraltet ist, lädt genau einmal`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)
        veraltet.markStale(PropertyType.HOUSE)

        val vm = viewModel()
        sichtbar(vm)

        assertEquals(1, repository.pageRequests.size)
        assertFalse(veraltet.consume(PropertyType.HOUSE))
    }

    @Test
    fun `beim Neuladen nach Veraltet setzt sich der Kartenstapel zurück`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..4)
        val vm = viewModel()
        sichtbar(vm)
        vm.skip(vm.state.value.pager.items.first())
        assertEquals(listOf("2", "3", "4", "1"), vm.ids)

        veraltet.markStale(PropertyType.HOUSE)

        assertEquals(listOf("1", "2", "3", "4"), vm.ids)
    }
}
