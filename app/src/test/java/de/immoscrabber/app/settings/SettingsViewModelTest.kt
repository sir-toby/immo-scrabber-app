package de.immoscrabber.app.settings

import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun profil(id: String, type: PropertyType, city: String) = Suchprofil(
    id = id,
    propertyType = type,
    city = city,
    zipCode = null,
    radius = 20,
    excludedProviders = emptyList(),
    excludedSources = emptyList(),
    priceLimit = null,
    minRooms = null,
    minConstructionYear = null,
    maxConstructionYear = null,
    minArea = null,
)

/** Fake-Repository: jeder [laden]-Aufruf wartet auf das nächste Ergebnis aus [results]. */
private class FakeSuchprofilRepository : SuchprofilRepository {
    override val suchprofile = MutableStateFlow<List<Suchprofil>?>(null)
    var calls = 0
    var next = CompletableDeferred<ApiResult<List<Suchprofil>>>()

    override suspend fun laden(): ApiResult<List<Suchprofil>> {
        calls++
        val result = next.await()
        next = CompletableDeferred()
        if (result is ApiResult.Success) suchprofile.value = result.value
        return result
    }
}

private val network = ApiResult.Failure(ApiError.Network(IOException("offline")))

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeSuchprofilRepository()
    private var logouts = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        SettingsViewModel(repository, logout = { logouts++ }).also { runCurrent() }

    private val SettingsViewModel.ids
        get() = (state.value.suchprofile as SuchprofileState.Geladen).profile.map { it.id }

    @Test
    fun `erstes Öffnen lädt mit Spinner und zeigt die Profile sortiert`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(SuchprofileState.Laden, vm.state.value.suchprofile)

        repository.next.complete(
            ApiResult.Success(
                listOf(
                    profil("s", PropertyType.SITE, "Erlangen"),
                    profil("h", PropertyType.HOUSE, "Erlangen"),
                    profil("f", PropertyType.FLAT, "Erlangen"),
                ),
            ),
        )
        runCurrent()

        assertEquals(listOf("h", "f", "s"), vm.ids)
        assertEquals(1, repository.calls)
    }

    @Test
    fun `schon geladene Profile kommen aus dem Speicher ohne neuen Aufruf`() = runTest(dispatcher) {
        repository.suchprofile.value = listOf(profil("h", PropertyType.HOUSE, "Erlangen"))

        val vm = viewModel()

        assertEquals(listOf("h"), vm.ids)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `Fehler beim ersten Laden, Erneut versuchen lädt nochmal`() = runTest(dispatcher) {
        val vm = viewModel()
        repository.next.complete(network)
        runCurrent()
        assertEquals(SuchprofileState.Fehler, vm.state.value.suchprofile)

        vm.retry()
        assertEquals(SuchprofileState.Laden, vm.state.value.suchprofile)
        repository.next.complete(ApiResult.Success(listOf(profil("h", PropertyType.HOUSE, "Erlangen"))))
        runCurrent()

        assertEquals(listOf("h"), vm.ids)
    }

    @Test
    fun `Pull-to-Refresh lädt neu, die alte Liste bleibt solange stehen`() = runTest(dispatcher) {
        repository.suchprofile.value = listOf(profil("h", PropertyType.HOUSE, "Erlangen"))
        val vm = viewModel()

        vm.refresh()
        assertTrue(vm.state.value.refreshing)
        assertEquals(listOf("h"), vm.ids)
        repository.next.complete(
            ApiResult.Success(listOf(profil("h", PropertyType.HOUSE, "Erlangen"), profil("f", PropertyType.FLAT, "Bamberg"))),
        )
        runCurrent()

        assertFalse(vm.state.value.refreshing)
        assertEquals(listOf("h", "f"), vm.ids)
    }

    @Test
    fun `gescheitertes Pull-to-Refresh behält die Liste und meldet den Fehler`() = runTest(dispatcher) {
        repository.suchprofile.value = listOf(profil("h", PropertyType.HOUSE, "Erlangen"))
        val vm = viewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { vm.events.toList(events) }

        vm.refresh()
        repository.next.complete(network)
        runCurrent()

        assertFalse(vm.state.value.refreshing)
        assertEquals(listOf("h"), vm.ids)
        assertEquals(listOf(SettingsEvent.RefreshFailed), events)
    }

    @Test
    fun `ein neu geladenes Repository erscheint sofort`() = runTest(dispatcher) {
        repository.suchprofile.value = listOf(profil("h", PropertyType.HOUSE, "Erlangen"))
        val vm = viewModel()

        repository.suchprofile.value = listOf(profil("x", PropertyType.SITE, "Fürth"))
        runCurrent()

        assertEquals(listOf("x"), vm.ids)
    }

    @Test
    fun `bei 10 Profilen ist das Maximum erreicht`() = runTest(dispatcher) {
        repository.suchprofile.value = (1..9).map { profil("$it", PropertyType.HOUSE, "Ort $it") }
        val vm = viewModel()
        assertFalse((vm.state.value.suchprofile as SuchprofileState.Geladen).maximumErreicht)

        repository.suchprofile.value = (1..10).map { profil("$it", PropertyType.HOUSE, "Ort $it") }
        runCurrent()

        assertTrue((vm.state.value.suchprofile as SuchprofileState.Geladen).maximumErreicht)
    }

    @Test
    fun `Abmelden meldet die Sitzung ab`() = runTest(dispatcher) {
        repository.suchprofile.value = emptyList()
        val vm = viewModel()

        vm.abmelden()
        runCurrent()

        assertEquals(1, logouts)
    }
}
