package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.SuchprofilInput
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.MockApiRule
import de.immoscrabber.app.core.network.jsonResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SuchprofilRepositoryTest {
    @get:Rule val mock = MockApiRule()

    private val veraltet = VeraltetMerker(now = { Duration.ZERO }, threshold = 30.minutes)
    private val repository by lazy { ApiSuchprofilRepository(mock.api, veraltet) }

    private val flat = SuchprofilInput(PropertyType.FLAT, city = "Erlangen", zipCode = "91054", radius = 20)

    private fun emptyList() = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("[]")

    /** Lädt die drei Profile der Fixture (je Typ eins) und verwirft die Anfrage. */
    private suspend fun dreiProfileGeladen() {
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))
        repository.laden()
        mock.takeRequest()
    }

    @Test
    fun `vor dem ersten Laden ist nichts im Speicher`() {
        assertNull(repository.suchprofile.value)
    }

    @Test
    fun `Laden holt GET preferences und behält die Liste im Speicher`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = repository.laden()

        assertEquals("/api/preferences", mock.takeRequest().path)
        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("Erlangen", "Erlangen", "Erlangen"), repository.suchprofile.value?.map { it.city })
    }

    @Test
    fun `gescheitertes Neuladen lässt die alte Liste stehen`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))
        repository.laden()
        mock.enqueue(MockResponse().setResponseCode(503))

        val result = repository.laden()

        assertEquals(ApiResult.Failure(ApiError.Http(503)), result)
        assertEquals(3, repository.suchprofile.value?.size)
    }

    @Test
    fun `Anlegen schickt POST, lädt danach neu und markiert nur den Typ als veraltet`() = runTest {
        dreiProfileGeladen()
        mock.enqueue(jsonResponse(201, "preferences/preference_created_201.json"))
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = repository.speichern(id = null, input = flat)

        assertEquals(ApiResult.Success(Unit), result)
        mock.takeRequest().let {
            assertEquals("POST", it.method)
            assertEquals("/api/preferences", it.path)
        }
        mock.takeRequest().let {
            assertEquals("GET", it.method)
            assertEquals("/api/preferences", it.path)
        }
        assertEquals(setOf(PropertyType.FLAT), veraltet.stale.value)
    }

    @Test
    fun `Ändern schickt PATCH an die uuid und lädt neu`() = runTest {
        dreiProfileGeladen()
        mock.enqueue(jsonResponse(200, "preferences/preference_created_201.json"))
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = repository.speichern(id = "abc", input = flat)

        assertEquals(ApiResult.Success(Unit), result)
        mock.takeRequest().let {
            assertEquals("PATCH", it.method)
            assertEquals("/api/preferences/abc", it.path)
        }
        assertEquals("GET", mock.takeRequest().method)
        assertEquals(setOf(PropertyType.FLAT), veraltet.stale.value)
    }

    @Test
    fun `Löschen schickt DELETE, lädt neu und markiert den Typ des Profils`() = runTest {
        dreiProfileGeladen()
        val site = repository.suchprofile.value!!.single { it.propertyType == PropertyType.SITE }
        mock.enqueue(jsonResponse(200, "preferences/preference_deleted_200.json"))
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = repository.loeschen(site)

        assertEquals(ApiResult.Success(Unit), result)
        mock.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/preferences/${site.id}", it.path)
        }
        assertEquals("GET", mock.takeRequest().method)
        assertEquals(setOf(PropertyType.SITE), veraltet.stale.value)
    }

    @Test
    fun `gescheitertes Speichern lädt nicht neu und markiert nichts`() = runTest {
        dreiProfileGeladen()
        mock.enqueue(MockResponse().setResponseCode(500))

        val result = repository.speichern(id = null, input = flat)

        assertEquals(ApiResult.Failure(ApiError.Http(500)), result)
        assertEquals("nur GET und POST", 2, mock.server.requestCount)
        assertEquals(emptySet<PropertyType>(), veraltet.stale.value)
    }

    @Test
    fun `scheitert nur das Neuladen, gilt das Speichern trotzdem`() = runTest {
        dreiProfileGeladen()
        mock.enqueue(jsonResponse(201, "preferences/preference_created_201.json"))
        mock.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ApiResult.Success(Unit), repository.speichern(id = null, input = flat))
        assertEquals(setOf(PropertyType.FLAT), veraltet.stale.value)
    }

    @Test
    fun `das erste Suchprofil markiert alle Typen, denn bisher zeigten alle Tabs Noch kein Suchprofil`() = runTest {
        mock.enqueue(emptyList())
        repository.laden()
        mock.enqueue(jsonResponse(201, "preferences/preference_created_201.json"))
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        repository.speichern(id = null, input = flat)

        assertEquals(PropertyType.entries.toSet(), veraltet.stale.value)
    }

    @Test
    fun `Anlegen ohne geladene Liste (aus dem Tab ohne Suchprofil) markiert ebenfalls alle Typen`() = runTest {
        mock.enqueue(jsonResponse(201, "preferences/preference_created_201.json"))
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        repository.speichern(id = null, input = flat)

        assertEquals(PropertyType.entries.toSet(), veraltet.stale.value)
    }

    @Test
    fun `das letzte Suchprofil löschen markiert alle Typen`() = runTest {
        dreiProfileGeladen()
        val house = repository.suchprofile.value!!.first()
        mock.enqueue(jsonResponse(200, "preferences/preference_deleted_200.json"))
        mock.enqueue(emptyList())

        repository.loeschen(house)

        assertEquals(PropertyType.entries.toSet(), veraltet.stale.value)
    }
}
