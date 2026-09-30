package de.immoscrabber.app.core.network.dto

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Antwort von `GET /properties/results`: immer alle drei Listen. */
@Serializable
internal data class ResultsDto(
    val houses: List<PropertyDto> = emptyList(),
    val flats: List<PropertyDto> = emptyList(),
    val sites: List<PropertyDto> = emptyList(),
) {
    fun listFor(type: PropertyType) = when (type) {
        PropertyType.HOUSE -> houses
        PropertyType.FLAT -> flats
        PropertyType.SITE -> sites
    }
}

/**
 * `HouseResponse` bzw. `SiteResponse` (`api/property/models.py`). Grundstücken fehlen
 * die Haus-Felder; Zahlen als Double, weil die DB Floats liefert.
 */
@Serializable
internal data class PropertyDto(
    val id: String? = null,
    val title: String? = null,
    val image: String? = null,
    val price: Double? = null,
    val location: LocationDto? = null,
    val rooms: Double? = null,
    @SerialName("area_building") val areaBuilding: Double? = null,
    @SerialName("area_estate") val areaEstate: Double? = null,
    val provider: String? = null,
    val url: String? = null,
    val source: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val label: String? = null,
    val constructionYear: Int? = null,
    val energyEfficiencyClass: String? = null,
    @SerialName("property_type") val propertyType: String? = null,
)

@Serializable
internal data class LocationDto(
    val zipCode: String? = null,
    val city: String? = null,
    val street: String? = null,
    val number: String? = null,
)

@Serializable
internal data class LabelRequestDto(val label: String)

@Serializable
internal data class LabelResponseDto(val propertyId: String? = null, val label: String? = null)

@Serializable
internal data class BulkLabelRequestDto(val propertyType: String, val label: String)

@Serializable
internal data class BulkLabelResponseDto(val updated: Int? = null)

internal fun PropertyDto.toInserat(id: String, requestedType: PropertyType) = Inserat(
    id = id,
    propertyType = PropertyType.fromApiValue(propertyType) ?: requestedType,
    title = title,
    imageUrl = image,
    price = price,
    zipCode = location?.zipCode,
    city = location?.city,
    street = location?.street,
    houseNumber = location?.number,
    rooms = rooms,
    livingArea = areaBuilding,
    plotArea = areaEstate,
    anbieter = provider,
    url = url,
    source = source,
    createdAt = createdAt,
    label = Label.fromApiValue(label),
    constructionYear = constructionYear,
    energyEfficiencyClass = energyEfficiencyClass,
)
