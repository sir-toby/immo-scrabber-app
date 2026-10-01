package de.immoscrabber.app.properties

import de.immoscrabber.app.core.data.VeraltetMerker
import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Leere Liste eines Typs ohne eigenes Suchprofil: „Kein Suchprofil für Grundstücke“ (#13). */
@OptIn(ExperimentalCoroutinesApi::class)
class LeerzustandOhneSuchprofilTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeInseratRepository()
    private val veraltet = VeraltetMerker(now = { Duration.ZERO }, threshold = Duration.INFINITE)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.grundstuecke(suchprofile: FakeSuchprofile): PropertyListViewModel =
        testViewModel(repository, veraltet, type = PropertyType.SITE, suchprofile = suchprofile).also { runCurrent() }

    @Test
    fun `leere Liste ohne Suchprofil des Typs zeigt den eigenen Leerzustand`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        val suchprofile = FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE), suchprofil(PropertyType.FLAT)))

        val vm = grundstuecke(suchprofile)

        assertTrue(vm.state.value.keinSuchprofilFuerTyp)
        assertEquals("der Speicher reicht, kein neues GET /preferences", 0, suchprofile.ladenCalls)
    }

    @Test
    fun `leere Liste mit Suchprofil des Typs bleibt beim normalen Leerzustand`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)

        val vm = grundstuecke(FakeSuchprofile(listOf(suchprofil(PropertyType.SITE))))

        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
    }

    @Test
    fun `volle Liste zeigt den Hinweis nie`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..3)

        val vm = grundstuecke(FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE))))

        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
    }

    @Test
    fun `solange die erste Seite lädt, kein Hinweis`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        repository.pageGate = CompletableDeferred()

        val vm = grundstuecke(FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE))))

        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
    }

    @Test
    fun `leere Seite mit weiteren Seiten zeigt keinen Hinweis`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..2)
        repository.pages[Label.INTERESSANT] = ApiResult.Success(InseratPage(emptyList(), PageCursor("t9", "9")))
        val vm = grundstuecke(FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE))))

        vm.selectFilter(Filter.Favoriten)
        runCurrent()

        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
    }

    @Test
    fun `Pull-to-Refresh holt beim Hinweis die Suchprofile neu`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        val suchprofile = FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE)))
        val vm = grundstuecke(suchprofile)
        assertTrue(vm.state.value.keinSuchprofilFuerTyp)

        // Im Web angelegt: Die Liste bleibt (noch) leer, das Profil gibt es jetzt.
        suchprofile.ladenResult = ApiResult.Success(listOf(suchprofil(PropertyType.HOUSE), suchprofil(PropertyType.SITE)))
        vm.refresh()
        runCurrent()

        assertEquals(1, suchprofile.ladenCalls)
        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
    }

    @Test
    fun `Veraltet holt beim Hinweis die Suchprofile neu`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        val suchprofile = FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE)))
        val vm = grundstuecke(suchprofile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.watchStale() }

        suchprofile.ladenResult = ApiResult.Success(listOf(suchprofil(PropertyType.HOUSE), suchprofil(PropertyType.SITE)))
        veraltet.markStale(PropertyType.SITE)
        runCurrent()

        assertEquals(1, suchprofile.ladenCalls)
        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
    }

    @Test
    fun `Pull-to-Refresh ohne Hinweis lädt die Suchprofile nicht`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..2)
        val suchprofile = FakeSuchprofile(listOf(suchprofil(PropertyType.SITE)))
        val vm = grundstuecke(suchprofile)

        vm.refresh()
        runCurrent()

        assertEquals(0, suchprofile.ladenCalls)
    }

    @Test
    fun `noch nie geladene Suchprofile werden bei leerer Liste geholt`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        val suchprofile = FakeSuchprofile(null).apply {
            ladenResult = ApiResult.Success(listOf(suchprofil(PropertyType.HOUSE)))
        }

        val vm = grundstuecke(suchprofile)

        assertEquals(1, suchprofile.ladenCalls)
        assertTrue(vm.state.value.keinSuchprofilFuerTyp)
        assertFalse(vm.state.value.suchprofilePruefen)
    }

    @Test
    fun `scheitert das Laden der Suchprofile, bleibt der normale Leerzustand`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        val suchprofile = FakeSuchprofile(null).apply { ladenResult = networkError }

        val vm = grundstuecke(suchprofile)

        assertEquals(1, suchprofile.ladenCalls)
        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
        assertFalse(vm.state.value.suchprofilePruefen)
    }

    @Test
    fun `nach dem Anlegen im Editor läuft der Tab normal weiter`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        val suchprofile = FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE)))
        val vm = grundstuecke(suchprofile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.watchStale() }
        assertTrue(vm.state.value.keinSuchprofilFuerTyp)

        // Wie ApiSuchprofilRepository nach dem Speichern: Liste neu geladen, Typ veraltet.
        repository.pages[Label.UNBEWERTET] = page(1..2)
        suchprofile.suchprofile.value = listOf(suchprofil(PropertyType.HOUSE), suchprofil(PropertyType.SITE))
        veraltet.markStale(PropertyType.SITE)
        runCurrent()

        assertFalse(vm.state.value.keinSuchprofilFuerTyp)
        assertEquals(listOf("1", "2"), vm.state.value.pager.items.map { it.id })
    }

    @Test
    fun `nach dem Löschen des letzten Profils des Typs erscheint der Hinweis`() = runTest(dispatcher) {
        repository.pages[Label.UNBEWERTET] = page(1..2)
        val suchprofile = FakeSuchprofile(listOf(suchprofil(PropertyType.HOUSE), suchprofil(PropertyType.SITE)))
        val vm = grundstuecke(suchprofile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.watchStale() }

        repository.pages[Label.UNBEWERTET] = page(IntRange.EMPTY)
        suchprofile.suchprofile.value = listOf(suchprofil(PropertyType.HOUSE))
        veraltet.markStale(PropertyType.SITE)
        runCurrent()

        assertTrue(vm.state.value.keinSuchprofilFuerTyp)
    }
}
