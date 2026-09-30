package de.immoscrabber.app.core.network

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreferencesAndDevicesApiTest {
    @get:Rule val mock = MockApiRule()

    private val input = SuchprofilInput(
        propertyType = PropertyType.HOUSE,
        city = "Erlangen",
        zipCode = "91054",
        radius = 20,
        excludedProviders = listOf("Musterbau GmbH"),
        priceLimit = 500000,
        minRooms = 4,
        minConstructionYear = 1970,
    )

    private val expectedCreated = Suchprofil(
        id = "0b7e7a52-3a8e-4d0e-9c55-6c1f0d6f6a01",
        propertyType = PropertyType.HOUSE,
        city = "Erlangen",
        zipCode = "91054",
        radius = 20,
        excludedProviders = listOf("Musterbau GmbH"),
        excludedSources = emptyList(),
        priceLimit = 500000,
        minRooms = 4,
        minConstructionYear = 1970,
        maxConstructionYear = null,
        minArea = null,
    )

    /** Alle Felder gehen raus, auch `null`, damit ein Wert im Editor gelöscht werden kann. */
    private val expectedBody =
        """{"property_type":"house","city":"Erlangen","zipCode":"91054","radius":20,""" +
            """"provider_blacklist":["Musterbau GmbH"],"source_blacklist":[],"priceLimit":500000,""" +
            """"minRooms":4,"minConstructionYear":1970,"maxConstructionYear":null,"minArea":null}"""

    @Test
    fun `Suchprofile laden mappt den Typ aus der Großschreibung`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = mock.api.suchprofile() as ApiResult.Success

        assertEquals("/api/preferences", mock.takeRequest().path)
        assertEquals(listOf(PropertyType.HOUSE, PropertyType.FLAT, PropertyType.SITE), result.value.map { it.propertyType })
        assertEquals(
            Suchprofil(
                id = "11f1315e-7c60-4575-8de2-f2f88a0aa89b",
                propertyType = PropertyType.HOUSE,
                city = "Erlangen",
                zipCode = "91054",
                radius = 20,
                excludedProviders = emptyList(),
                excludedSources = emptyList(),
                priceLimit = null,
                minRooms = null,
                minConstructionYear = null,
                maxConstructionYear = null,
                minArea = null,
            ),
            result.value.first(),
        )
    }

    @Test
    fun `Suchprofil anlegen schickt POST mit allen Feldern`() = runTest {
        mock.enqueue(jsonResponse(201, "preferences/preference_created_201.json"))

        val result = mock.api.suchprofilAnlegen(input)

        assertEquals(ApiResult.Success(expectedCreated), result)
        val request = mock.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/preferences", request.path)
        assertEquals(expectedBody, request.body.readUtf8())
    }

    @Test
    fun `mehr als 10 Suchprofile ist 400 mit Servertext`() = runTest {
        mock.enqueue(jsonResponse(400, "preferences/preferences_limit_400.json"))

        assertEquals(
            ApiResult.Failure(ApiError.BadRequest("Maximum of 10 search preferences allowed per user.")),
            mock.api.suchprofilAnlegen(input),
        )
    }

    @Test
    fun `Suchprofil ändern schickt PATCH an die uuid`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preference_created_201.json"))

        val result = mock.api.suchprofilAendern("0b7e7a52-3a8e-4d0e-9c55-6c1f0d6f6a01", input)

        assertEquals(ApiResult.Success(expectedCreated), result)
        val request = mock.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/preferences/0b7e7a52-3a8e-4d0e-9c55-6c1f0d6f6a01", request.path)
        assertEquals(expectedBody, request.body.readUtf8())
    }

    @Test
    fun `Suchprofil löschen schickt DELETE`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preference_deleted_200.json"))

        assertEquals(ApiResult.Success(Unit), mock.api.suchprofilLoeschen("11f1315e"))
        val request = mock.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/preferences/11f1315e", request.path)
    }

    @Test
    fun `unbekanntes Suchprofil löschen ist HTTP 404`() = runTest {
        mock.enqueue(jsonResponse(404, "preferences/preference_404.json"))

        assertEquals(ApiResult.Failure(ApiError.Http(404)), mock.api.suchprofilLoeschen("weg"))
    }

    @Test
    fun `Gerät registrieren schickt PUT mit dem FCM-Token`() = runTest {
        mock.enqueue(MockResponse().setResponseCode(204))

        assertEquals(ApiResult.Success(Unit), mock.api.geraetRegistrieren("fcm-token-1"))
        val request = mock.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/devices", request.path)
        assertEquals("""{"token":"fcm-token-1"}""", request.body.readUtf8())
    }

    @Test
    fun `Gerät registrieren ohne Token ist 400`() = runTest {
        mock.enqueue(jsonResponse(400, "devices/devices_400_token_missing.json"))

        assertEquals(ApiResult.Failure(ApiError.BadRequest("token missing")), mock.api.geraetRegistrieren(" "))
    }

    @Test
    fun `Gerät abmelden schickt DELETE mit dem Token im Pfad`() = runTest {
        mock.enqueue(MockResponse().setResponseCode(204))

        assertEquals(ApiResult.Success(Unit), mock.api.geraetAbmelden("abc:DEF_123-x"))
        val request = mock.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/devices/abc:DEF_123-x", request.path)
    }
}
