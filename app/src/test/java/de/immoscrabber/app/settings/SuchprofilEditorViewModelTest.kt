package de.immoscrabber.app.settings

import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private val haus = Suchprofil(
    id = "h1",
    propertyType = PropertyType.HOUSE,
    city = "Erlangen",
    zipCode = "91054",
    radius = 20,
    ausgeschlosseneAnbieter = emptyList(),
    excludedSources = emptyList(),
    priceLimit = null,
    minRooms = null,
    minConstructionYear = null,
    maxConstructionYear = null,
    minArea = null,
)

/** Fake: [speichern] und [loeschen] warten auf [next]. */
private class FakeRepository : SuchprofilRepository {
    override val suchprofile = MutableStateFlow<List<Suchprofil>?>(listOf(haus))
    val gespeichert = mutableListOf<Pair<String?, SuchprofilInput>>()
    val geloescht = mutableListOf<Suchprofil>()
    var next = CompletableDeferred<ApiResult<Unit>>()

    override suspend fun laden(): ApiResult<List<Suchprofil>> = error("lädt das Repository selbst")

    override suspend fun speichern(id: String?, input: SuchprofilInput): ApiResult<Unit> {
        gespeichert += id to input
        return next.await().also { next = CompletableDeferred() }
    }

    override suspend fun loeschen(profil: Suchprofil): ApiResult<Unit> {
        geloescht += profil
        return next.await().also { next = CompletableDeferred() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuchprofilEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun neu(type: PropertyType? = PropertyType.FLAT) = SuchprofilEditorViewModel(repository, profilId = null, vorauswahl = type)

    private fun bearbeiten() = SuchprofilEditorViewModel(repository, profilId = "h1", vorauswahl = null)

    private fun TestScope.events(vm: SuchprofilEditorViewModel): List<EditorEvent> {
        val events = mutableListOf<EditorEvent>()
        backgroundScope.launch(dispatcher) { vm.events.toList(events) }
        return events
    }

    private fun SuchprofilEditorViewModel.ausfuellen() = aendern { it.copy(zipCode = "91054", city = "Erlangen") }

    @Test
    fun `neues Profil startet mit dem Typ des Tabs und ohne Änderungen`() {
        val vm = neu(PropertyType.SITE)

        assertTrue(vm.state.value.neu)
        assertEquals(PropertyType.SITE, vm.state.value.form.type)
        assertFalse(vm.state.value.geaendert)
    }

    @Test
    fun `Bearbeiten füllt das Formular aus dem geladenen Profil`() {
        val vm = bearbeiten()

        assertFalse(vm.state.value.neu)
        assertEquals("Erlangen", vm.state.value.form.city)
        assertEquals(PropertyType.HOUSE, vm.state.value.form.type)
    }

    @Test
    fun `der Typ eines bestehenden Profils ist fix`() {
        val vm = bearbeiten()

        vm.aendern { it.copy(type = PropertyType.SITE) }

        assertEquals(PropertyType.HOUSE, vm.state.value.form.type)
    }

    @Test
    fun `geändert ist nur, was vom Ausgangszustand abweicht`() {
        val vm = bearbeiten()

        vm.aendern { it.copy(city = "Nürnberg") }
        assertTrue(vm.state.value.geaendert)

        vm.aendern { it.copy(city = "Erlangen") }
        assertFalse(vm.state.value.geaendert)
    }

    @Test
    fun `Fehler erscheinen erst nach dem ersten Speichern-Versuch, dann live`() {
        val vm = neu()
        assertEquals(emptyMap<Feld, Feldfehler>(), vm.state.value.fehler)

        vm.speichern()

        assertEquals(Feldfehler.PlzUngueltig, vm.state.value.fehler[Feld.Plz])
        assertTrue(repository.gespeichert.isEmpty())

        vm.aendern { it.copy(zipCode = "91054") }
        assertNull(vm.state.value.fehler[Feld.Plz])
    }

    @Test
    fun `Speichern sperrt das Formular und meldet bei neuem Profil den neuen Ort`() = runTest(dispatcher) {
        val vm = neu()
        val events = events(vm)
        vm.ausfuellen()

        vm.speichern()

        assertTrue(vm.state.value.gesperrt)
        assertEquals(listOf(null to SuchprofilInput(PropertyType.FLAT, "Erlangen", "91054", 20)), repository.gespeichert)
        vm.speichern()
        assertEquals("zweites Speichern während des ersten wird ignoriert", 1, repository.gespeichert.size)

        repository.next.complete(ApiResult.Success(Unit))

        assertEquals(listOf<EditorEvent>(EditorEvent.Gespeichert(neuerOrt = true)), events)
    }

    @Test
    fun `nur Limits geändert meldet keinen neuen Ort`() = runTest(dispatcher) {
        val vm = bearbeiten()
        val events = events(vm)
        vm.aendern { it.copy(priceLimit = "500000") }

        vm.speichern()
        repository.next.complete(ApiResult.Success(Unit))

        assertEquals("h1", repository.gespeichert.single().first)
        assertEquals(listOf<EditorEvent>(EditorEvent.Gespeichert(neuerOrt = false)), events)
    }

    @Test
    fun `gescheitertes Speichern entsperrt, behält das Formular und meldet den Fehler`() = runTest(dispatcher) {
        val vm = neu()
        val events = events(vm)
        vm.ausfuellen()

        vm.speichern()
        repository.next.complete(ApiResult.Failure(ApiError.Http(500)))

        assertFalse(vm.state.value.gesperrt)
        assertEquals("Erlangen", vm.state.value.form.city)
        assertEquals(listOf<EditorEvent>(EditorEvent.Fehler(EditorFehler.OrtNichtGefunden, Aktion.Speichern)), events)
    }

    @Test
    fun `Löschen löscht das Profil und meldet es`() = runTest(dispatcher) {
        val vm = bearbeiten()
        val events = events(vm)

        vm.loeschen()
        assertTrue(vm.state.value.gesperrt)
        repository.next.complete(ApiResult.Success(Unit))

        assertEquals(listOf(haus), repository.geloescht)
        assertEquals(listOf<EditorEvent>(EditorEvent.Geloescht), events)
    }

    @Test
    fun `gescheitertes Löschen meldet den Fehler`() = runTest(dispatcher) {
        val vm = bearbeiten()
        val events = events(vm)

        vm.loeschen()
        repository.next.complete(ApiResult.Failure(ApiError.Network(IOException())))

        assertFalse(vm.state.value.gesperrt)
        assertEquals(listOf<EditorEvent>(EditorEvent.Fehler(EditorFehler.NichtErreichbar, Aktion.Loeschen)), events)
    }

    @Test
    fun `ein unbekanntes Profil (etwa nach dem Beenden durch das System) meldet sich als nicht gefunden`() {
        val vm = SuchprofilEditorViewModel(repository, profilId = "weg", vorauswahl = null)

        assertTrue(vm.state.value.nichtGefunden)
    }
}
