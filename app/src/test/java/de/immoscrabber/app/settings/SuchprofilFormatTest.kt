package de.immoscrabber.app.settings

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import org.junit.Assert.assertEquals
import org.junit.Test

private fun profil(
    id: String = "1",
    type: PropertyType? = PropertyType.HOUSE,
    city: String? = "Erlangen",
    zipCode: String? = "91054",
    radius: Int? = 20,
    priceLimit: Int? = null,
    minRooms: Int? = null,
    minConstructionYear: Int? = null,
    maxConstructionYear: Int? = null,
    minArea: Int? = null,
) = Suchprofil(
    id = id,
    propertyType = type,
    city = city,
    zipCode = zipCode,
    radius = radius,
    ausgeschlosseneAnbieter = emptyList(),
    excludedSources = emptyList(),
    priceLimit = priceLimit,
    minRooms = minRooms,
    minConstructionYear = minConstructionYear,
    maxConstructionYear = maxConstructionYear,
    minArea = minArea,
)

class SuchprofilFormatTest {
    @Test
    fun `Titel ist Stadt mit Umkreis`() {
        assertEquals("Erlangen (+20 km)", formatSuchprofilTitel(profil()))
    }

    @Test
    fun `Titel ohne Stadt nimmt die PLZ, ohne Umkreis nur den Ort`() {
        assertEquals("91054 (+5 km)", formatSuchprofilTitel(profil(city = " ", radius = 5)))
        assertEquals("Erlangen", formatSuchprofilTitel(profil(radius = null)))
        assertEquals("Unbekannter Ort", formatSuchprofilTitel(profil(city = null, zipCode = null, radius = null)))
    }

    @Test
    fun `ohne gesetzte Limits steht ohne Limits`() {
        assertEquals("ohne Limits", formatLimits(profil()))
    }

    @Test
    fun `Haus mit allen Limits wie im Beispiel der Entscheidung`() {
        val haus = profil(
            priceLimit = 600_000,
            minRooms = 4,
            minConstructionYear = 1990,
            maxConstructionYear = 2020,
            minArea = 500,
        )
        assertEquals("bis 600.000 € · ab 4 Zi. · Bj. 1990–2020 · ab 500 m² Grundst.", formatLimits(haus))
    }

    @Test
    fun `Baujahr nur von oder nur bis`() {
        assertEquals("Bj. ab 1995", formatLimits(profil(type = PropertyType.FLAT, minConstructionYear = 1995)))
        assertEquals("Bj. bis 1970", formatLimits(profil(maxConstructionYear = 1970)))
    }

    @Test
    fun `Grundstück zeigt die Fläche ohne Zusatz`() {
        val grundstueck = profil(type = PropertyType.SITE, priceLimit = 250_000, minArea = 800)
        assertEquals("bis 250.000 € · ab 800 m²", formatLimits(grundstueck))
    }

    @Test
    fun `sortiert nach Typ in Tab-Reihenfolge, dann nach Stadt`() {
        val profile = listOf(
            profil(id = "site-a", type = PropertyType.SITE, city = "Ansbach"),
            profil(id = "house-n", type = PropertyType.HOUSE, city = "Nürnberg"),
            profil(id = "flat-e", type = PropertyType.FLAT, city = "Erlangen"),
            profil(id = "house-oe", type = PropertyType.HOUSE, city = "Östringen"),
            profil(id = "house-b", type = PropertyType.HOUSE, city = "bamberg"),
            profil(id = "unknown", type = null, city = "Aachen"),
        )
        assertEquals(
            listOf("house-b", "house-n", "house-oe", "flat-e", "site-a", "unknown"),
            sortiereSuchprofile(profile).map { it.id },
        )
    }
}

class KontoFormatTest {
    @Test
    fun `Host ist der Servername ohne Schema und Pfad`() {
        assertEquals("immo.example.com", formatHost("https://immo.example.com/api/"))
    }

    @Test
    fun `Host behält einen abweichenden Port`() {
        assertEquals("10.0.2.2:5000", formatHost("http://10.0.2.2:5000/api/"))
    }

    @Test
    fun `unparsbare Adresse bleibt stehen`() {
        assertEquals("kaputt", formatHost("kaputt"))
    }
}
