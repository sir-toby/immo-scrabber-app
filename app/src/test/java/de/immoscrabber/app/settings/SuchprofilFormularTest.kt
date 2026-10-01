package de.immoscrabber.app.settings

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val erlangenHaus = SuchprofilFormular(
    type = PropertyType.HOUSE,
    zipCode = "91054",
    city = "Erlangen",
)

private fun profil(
    type: PropertyType = PropertyType.HOUSE,
    city: String? = "Erlangen",
    zipCode: String? = "91054",
    radius: Int? = 20,
    anbieter: List<String> = emptyList(),
    sources: List<String> = emptyList(),
    priceLimit: Int? = null,
    minRooms: Int? = null,
    minYear: Int? = null,
    maxYear: Int? = null,
    minArea: Int? = null,
) = Suchprofil(
    id = "p1",
    propertyType = type,
    city = city,
    zipCode = zipCode,
    radius = radius,
    ausgeschlosseneAnbieter = anbieter,
    excludedSources = sources,
    priceLimit = priceLimit,
    minRooms = minRooms,
    minConstructionYear = minYear,
    maxConstructionYear = maxYear,
    minArea = minArea,
)

class SuchprofilFormularTest {

    // --- Prüfen ---

    @Test
    fun `ein neues Formular hat Radius 20 und den vorausgewählten Typ`() {
        val form = SuchprofilFormular.neu(PropertyType.FLAT)

        assertEquals(PropertyType.FLAT, form.type)
        assertEquals("20", form.radius)
        assertEquals("", form.zipCode)
    }

    @Test
    fun `PLZ, Stadt und Radius genügen`() {
        assertEquals(emptyMap<Feld, Feldfehler>(), erlangenHaus.pruefen())
    }

    @Test
    fun `ohne Typ lässt sich nicht speichern`() {
        assertEquals(Feldfehler.TypFehlt, erlangenHaus.copy(type = null).pruefen()[Feld.Typ])
    }

    @Test
    fun `PLZ muss genau 5 Ziffern haben`() {
        listOf("", "9105", "910544", "9105a", "91 05").forEach { plz ->
            assertEquals(plz, Feldfehler.PlzUngueltig, erlangenHaus.copy(zipCode = plz).pruefen()[Feld.Plz])
        }
        assertNull(erlangenHaus.copy(zipCode = " 91054 ").pruefen()[Feld.Plz])
    }

    @Test
    fun `Stadt ist Pflicht, Leerzeichen zählen nicht`() {
        assertEquals(Feldfehler.StadtFehlt, erlangenHaus.copy(city = "   ").pruefen()[Feld.Stadt])
    }

    @Test
    fun `Radius ist Pflicht und größer 0`() {
        listOf("", "0", "-5", "abc").forEach { radius ->
            assertEquals(radius, Feldfehler.RadiusUngueltig, erlangenHaus.copy(radius = radius).pruefen()[Feld.Radius])
        }
        assertNull(erlangenHaus.copy(radius = "1").pruefen()[Feld.Radius])
    }

    @Test
    fun `Limits sind ganze Zahlen, leer ist erlaubt`() {
        val form = erlangenHaus.copy(priceLimit = "12a", minRooms = "3,5", minArea = "99999999999")

        val fehler = form.pruefen()

        assertEquals(Feldfehler.KeineGanzeZahl, fehler[Feld.Preis])
        assertEquals(Feldfehler.KeineGanzeZahl, fehler[Feld.Zimmer])
        assertEquals(Feldfehler.KeineGanzeZahl, fehler[Feld.Flaeche])
    }

    @Test
    fun `Baujahre sind vierstellig`() {
        val fehler = erlangenHaus.copy(minConstructionYear = "199", maxConstructionYear = "20201").pruefen()

        assertEquals(Feldfehler.BaujahrUngueltig, fehler[Feld.BaujahrVon])
        assertEquals(Feldfehler.BaujahrUngueltig, fehler[Feld.BaujahrBis])
    }

    @Test
    fun `Baujahr von darf nicht nach bis liegen, gleich ist erlaubt`() {
        val falsch = erlangenHaus.copy(minConstructionYear = "2021", maxConstructionYear = "2020")
        val gleich = erlangenHaus.copy(minConstructionYear = "2020", maxConstructionYear = "2020")

        assertEquals(Feldfehler.BaujahrReihenfolge, falsch.pruefen()[Feld.BaujahrBis])
        assertEquals(emptyMap<Feld, Feldfehler>(), gleich.pruefen())
    }

    @Test
    fun `ausgeblendete Felder werden nicht geprüft`() {
        // Wohnung: nur frühestes Baujahr; Grundstück: kein Zimmer, kein Baujahr.
        val wohnung = erlangenHaus.copy(type = PropertyType.FLAT, maxConstructionYear = "x", minArea = "x")
        val grundstueck = erlangenHaus.copy(type = PropertyType.SITE, minRooms = "x", minConstructionYear = "x")

        assertEquals(emptyMap<Feld, Feldfehler>(), wohnung.pruefen())
        assertEquals(emptyMap<Feld, Feldfehler>(), grundstueck.pruefen())
    }

    // --- Felder je Typ (wie SearchFormFields.vue, Entscheidung #8) ---

    @Test
    fun `Felder je Typ`() {
        val immer = setOf(Feld.Typ, Feld.Plz, Feld.Stadt, Feld.Radius)
        assertEquals(
            immer + setOf(Feld.Preis, Feld.Zimmer, Feld.BaujahrVon, Feld.BaujahrBis, Feld.Flaeche, Feld.Anbieter),
            felderFuer(PropertyType.HOUSE),
        )
        assertEquals(immer + setOf(Feld.Preis, Feld.Zimmer, Feld.BaujahrVon, Feld.Anbieter), felderFuer(PropertyType.FLAT))
        assertEquals(immer + setOf(Feld.Preis, Feld.Flaeche), felderFuer(PropertyType.SITE))
    }

    // --- Formular → Request ---

    @Test
    fun `leere Felder werden null, Text wird getrimmt`() {
        val form = erlangenHaus.copy(zipCode = " 91054", city = " Erlangen ", radius = "15")

        assertEquals(
            SuchprofilInput(
                propertyType = PropertyType.HOUSE,
                city = "Erlangen",
                zipCode = "91054",
                radius = 15,
            ),
            form.toInput(original = null),
        )
    }

    @Test
    fun `alle Limits eines Hauses gehen als Zahlen raus`() {
        val form = erlangenHaus.copy(
            priceLimit = "600000",
            minRooms = "4",
            minConstructionYear = "1990",
            maxConstructionYear = "2020",
            minArea = "500",
            ausgeschlosseneAnbieter = listOf("Allkauf", "Town & Country"),
        )

        val input = form.toInput(original = null)!!

        assertEquals(600000, input.priceLimit)
        assertEquals(4, input.minRooms)
        assertEquals(1990, input.minConstructionYear)
        assertEquals(2020, input.maxConstructionYear)
        assertEquals(500, input.minArea)
        assertEquals(listOf("Allkauf", "Town & Country"), input.ausgeschlosseneAnbieter)
    }

    @Test
    fun `ein noch nicht bestätigter Anbieter im Eingabefeld zählt mit, ohne Doppelte`() {
        val form = erlangenHaus.copy(ausgeschlosseneAnbieter = listOf("Allkauf"), anbieterEingabe = "  Bien-Zenker ")
        val doppelt = erlangenHaus.copy(ausgeschlosseneAnbieter = listOf("Allkauf"), anbieterEingabe = "allkauf")

        assertEquals(listOf("Allkauf", "Bien-Zenker"), form.toInput(null)!!.ausgeschlosseneAnbieter)
        assertEquals(listOf("Allkauf"), doppelt.toInput(null)!!.ausgeschlosseneAnbieter)
    }

    @Test
    fun `ungültiges Formular ergibt keinen Request`() {
        assertNull(erlangenHaus.copy(city = "").toInput(null))
    }

    @Test
    fun `was der Editor beim Typ nicht zeigt, bleibt wie im Original`() {
        val original = profil(
            type = PropertyType.SITE,
            anbieter = listOf("Allkauf"),
            sources = listOf("kleinanzeigen"),
            minRooms = 3,
            minYear = 1980,
        )
        val form = SuchprofilFormular.aus(original).copy(priceLimit = "150000", minArea = "")

        val input = form.toInput(original)!!

        assertEquals(150000, input.priceLimit)
        assertNull("sichtbar und geleert", input.minArea)
        assertEquals(listOf("Allkauf"), input.ausgeschlosseneAnbieter)
        assertEquals(listOf("kleinanzeigen"), input.excludedSources)
        assertEquals(3, input.minRooms)
        assertEquals(1980, input.minConstructionYear)
    }

    @Test
    fun `Formular aus einem Profil und unverändert zurück ergibt dasselbe Profil`() {
        val original = profil(
            type = PropertyType.HOUSE,
            radius = 30,
            anbieter = listOf("Allkauf"),
            priceLimit = 600000,
            minRooms = 4,
            minYear = 1990,
            maxYear = 2020,
            minArea = 500,
        )

        val form = SuchprofilFormular.aus(original)

        assertEquals("30", form.radius)
        assertEquals("600000", form.priceLimit)
        assertEquals(
            SuchprofilInput(
                propertyType = PropertyType.HOUSE,
                city = "Erlangen",
                zipCode = "91054",
                radius = 30,
                ausgeschlosseneAnbieter = listOf("Allkauf"),
                priceLimit = 600000,
                minRooms = 4,
                minConstructionYear = 1990,
                maxConstructionYear = 2020,
                minArea = 500,
            ),
            form.toInput(original),
        )
    }

    // --- Snackbar: neuer Ort? ---

    @Test
    fun `ein neues Profil bringt immer einen neuen Ort`() {
        assertTrue(ortGeaendert(original = null, input = erlangenHaus.toInput(null)!!))
    }

    @Test
    fun `nur Limits geändert ist kein neuer Ort`() {
        val input = erlangenHaus.copy(priceLimit = "500000", radius = "50").toInput(null)!!

        assertFalse(ortGeaendert(profil(), input))
    }

    @Test
    fun `andere PLZ oder Stadt ist ein neuer Ort, Groß-Kleinschreibung und Leerzeichen nicht`() {
        assertTrue(ortGeaendert(profil(), erlangenHaus.copy(zipCode = "91052").toInput(null)!!))
        assertTrue(ortGeaendert(profil(), erlangenHaus.copy(city = "Nürnberg").toInput(null)!!))
        assertFalse(ortGeaendert(profil(city = "Erlangen "), erlangenHaus.copy(city = "erlangen").toInput(null)!!))
    }
}
