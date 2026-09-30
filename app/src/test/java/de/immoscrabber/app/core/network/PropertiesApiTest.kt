package de.immoscrabber.app.core.network

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PropertiesApiTest {
    @get:Rule val mock = MockApiRule()

    private fun <T> ApiResult<T>.value(): T = (this as? ApiResult.Success)?.value
        ?: throw AssertionError("erwartet Success, war $this")

    @Test
    fun `erste Seite fragt einen Typ im Plural mit Seitengröße ab, ohne Label und Cursor`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_houses_page1.json"))

        mock.api.inserate(PropertyType.HOUSE, label = null)

        val request = mock.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/properties/results?propertyTypes=houses&pageSize=20", request.path)
    }

    @Test
    fun `ein Haus wird vollständig gemappt`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_houses_page1.json"))

        val first = mock.api.inserate(PropertyType.HOUSE, label = null).value().inserate.first()

        assertEquals(
            Inserat(
                id = "472f14b1-86ab-4462-88bc-a355ba39cfcc",
                propertyType = PropertyType.HOUSE,
                title = "Einfamilienhaus mit Garten Nr. 1 – ruhige Lage, viel Platz für die Familie",
                imageUrl = "https://picsum.photos/seed/immo0/800/600",
                price = 904000.0,
                zipCode = "91054",
                city = "Erlangen",
                street = null,
                houseNumber = null,
                rooms = 5.0,
                livingArea = 118.0,
                plotArea = 275.0,
                anbieter = "Makler Müller",
                url = "https://example.com/haus/0",
                source = "Kleinanzeigen",
                createdAt = "2026-09-30T09:16:10.177763",
                label = Label.UNBEWERTET,
                constructionYear = 1967,
                energyEfficiencyClass = "A+",
            ),
            first,
        )
    }

    @Test
    fun `fehlende Werte bei Häusern kommen als null an`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_houses_page1.json"))

        val inserate = mock.api.inserate(PropertyType.HOUSE, label = null).value().inserate.associateBy { it.id }

        assertNull(inserate.getValue("d4385608-fb2d-4ff6-90b6-28977b31b66e").imageUrl)
        assertNull(inserate.getValue("d4385608-fb2d-4ff6-90b6-28977b31b66e").constructionYear)
        assertNull(inserate.getValue("d8031883-85dd-42f3-8534-6bbf8f134015").price)
        assertNull(inserate.getValue("a0738097-c7f7-444f-8be2-2e22cece055f").rooms)
        assertNull(inserate.getValue("112793b0-5e5a-4050-9072-cc7054dfc857").url)
    }

    @Test
    fun `volle Seite liefert den Cursor aus created_at und id des letzten Inserats`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_houses_page1.json"))

        val page = mock.api.inserate(PropertyType.HOUSE, label = null).value()

        assertEquals(20, page.inserate.size)
        assertEquals(PageCursor("2026-09-28T00:16:10.177763", "cd1df913-7e87-4ca5-a4ce-8130ec4c5fae"), page.nextCursor)
    }

    @Test
    fun `Folgeseite schickt den Cursor unverändert als beforeCreatedAt und beforeId`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_houses_page2.json"))

        mock.api.inserate(
            PropertyType.HOUSE,
            label = Label.UNBEWERTET,
            cursor = PageCursor("2026-09-28T00:16:10.177763", "cd1df913-7e87-4ca5-a4ce-8130ec4c5fae"),
        )

        val url = mock.takeRequest().requestUrl!!
        assertEquals("houses", url.queryParameter("propertyTypes"))
        assertEquals("unbewertet", url.queryParameter("label"))
        assertEquals("20", url.queryParameter("pageSize"))
        assertEquals("2026-09-28T00:16:10.177763", url.queryParameter("beforeCreatedAt"))
        assertEquals("cd1df913-7e87-4ca5-a4ce-8130ec4c5fae", url.queryParameter("beforeId"))
    }

    @Test
    fun `Seite kleiner als die Seitengröße ist das Ende`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_flats.json"))

        val page = mock.api.inserate(PropertyType.FLAT, label = null).value()

        assertEquals(12, page.inserate.size)
        assertNull(page.nextCursor)
        assertEquals(setOf(PropertyType.FLAT), page.inserate.map { it.propertyType }.toSet())
        assertEquals("propertyTypes=flats", mock.takeRequest().requestUrl!!.query!!.substringBefore('&'))
    }

    @Test
    fun `eigene Seitengröße bestimmt Anfrage und Ende`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_flats.json"))

        val page = mock.api.inserate(PropertyType.FLAT, label = null, pageSize = 12).value()

        assertEquals("12", mock.takeRequest().requestUrl!!.queryParameter("pageSize"))
        assertEquals("ff672a2e-8954-46b6-bd4e-e42768fd7732", page.inserate.first().id)
        assertEquals(page.inserate.last().id, page.nextCursor?.id)
    }

    @Test
    fun `Grundstücke ohne Haus-Felder werden mit null gemappt`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_sites.json"))

        val site = mock.api.inserate(PropertyType.SITE, label = null).value().inserate.first()

        assertEquals(PropertyType.SITE, site.propertyType)
        assertEquals("836c8102-5d4d-4bd0-bde5-23bb07d6a2c2", site.id)
        assertEquals(188000.0, site.price)
        assertEquals(670.0, site.plotArea)
        assertEquals("Sparkasse", site.source)
        assertNull(site.rooms)
        assertNull(site.livingArea)
        assertNull(site.anbieter)
        assertNull(site.constructionYear)
        assertNull(site.energyEfficiencyClass)
    }

    @Test
    fun `Label-Filter wird mitgeschickt und das Label gemappt`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/results_houses_interessant.json"))

        val page = mock.api.inserate(PropertyType.HOUSE, label = Label.INTERESSANT).value()

        assertEquals("interessant", mock.takeRequest().requestUrl!!.queryParameter("label"))
        assertEquals(listOf(Label.INTERESSANT), page.inserate.map { it.label })
    }

    @Test
    fun `ohne jedes Suchprofil kommt 400 mit Servertext`() = runTest {
        mock.enqueue(jsonResponse(400, "properties/results_400_kein_suchprofil.json"))

        assertEquals(
            ApiResult.Failure(ApiError.BadRequest("Keine Suchparameter gefunden")),
            mock.api.inserate(PropertyType.SITE, label = null),
        )
    }

    @Test
    fun `Bewerten schickt PATCH mit Singular-Typ und Label`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/label_200.json"))

        val result = mock.api.bewerten(PropertyType.FLAT, "a4283a64-5165-4343-84ab-b129244d570c", Label.INTERESSANT)

        assertEquals(ApiResult.Success(Unit), result)
        val request = mock.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/properties/flat/a4283a64-5165-4343-84ab-b129244d570c/label", request.path)
        assertEquals("""{"label":"interessant"}""", request.body.readUtf8())
    }

    @Test
    fun `Bewerten eines unbekannten Inserats ist HTTP 404`() = runTest {
        mock.enqueue(jsonResponse(404, "properties/label_404.json"))

        assertEquals(
            ApiResult.Failure(ApiError.Http(404)),
            mock.api.bewerten(PropertyType.HOUSE, "gibt-es-nicht", Label.UNINTERESSANT),
        )
    }

    @Test
    fun `alle neuen bewerten schickt den Singular-Typ und liefert die Anzahl`() = runTest {
        mock.enqueue(jsonResponse(200, "properties/labels_bulk_200.json"))

        val result = mock.api.alleNeuenBewerten(PropertyType.SITE, Label.UNINTERESSANT)

        assertEquals(ApiResult.Success(37), result)
        val request = mock.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/properties/labels", request.path)
        assertEquals("""{"propertyType":"site","label":"uninteressant"}""", request.body.readUtf8())
    }
}
