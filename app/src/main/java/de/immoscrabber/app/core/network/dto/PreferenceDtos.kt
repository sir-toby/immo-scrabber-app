package de.immoscrabber.app.core.network.dto

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `SearchPreferenceResponse` (`api/userPreferences/models.py`). */
@Serializable
internal data class SearchPreferenceDto(
    val uuid: String? = null,
    val userId: String? = null,
    @SerialName("property_type") val propertyType: String? = null,
    val city: String? = null,
    val zipCode: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val radius: Int? = null,
    @SerialName("provider_blacklist") val providerBlacklist: List<String>? = null,
    @SerialName("source_blacklist") val sourceBlacklist: List<String>? = null,
    val priceLimit: Int? = null,
    val minRooms: Int? = null,
    val minConstructionYear: Int? = null,
    val maxConstructionYear: Int? = null,
    val minArea: Int? = null,
)

/** Body für `POST /preferences` und `PATCH /preferences/<uuid>`; `null` wird mitgeschickt. */
@Serializable
internal data class SearchPreferenceRequestDto(
    @SerialName("property_type") val propertyType: String,
    val city: String,
    val zipCode: String,
    val radius: Int,
    @SerialName("provider_blacklist") val providerBlacklist: List<String>,
    @SerialName("source_blacklist") val sourceBlacklist: List<String>,
    val priceLimit: Int?,
    val minRooms: Int?,
    val minConstructionYear: Int?,
    val maxConstructionYear: Int?,
    val minArea: Int?,
)

@Serializable
internal data class MessageDto(val message: String? = null)

internal fun SearchPreferenceDto.toSuchprofil(id: String) = Suchprofil(
    id = id,
    propertyType = PropertyType.fromApiValue(propertyType),
    city = city,
    zipCode = zipCode,
    radius = radius,
    excludedProviders = providerBlacklist.orEmpty(),
    excludedSources = sourceBlacklist.orEmpty(),
    priceLimit = priceLimit,
    minRooms = minRooms,
    minConstructionYear = minConstructionYear,
    maxConstructionYear = maxConstructionYear,
    minArea = minArea,
)

internal fun SuchprofilInput.toDto() = SearchPreferenceRequestDto(
    propertyType = propertyType.apiValue,
    city = city,
    zipCode = zipCode,
    radius = radius,
    providerBlacklist = excludedProviders,
    sourceBlacklist = excludedSources,
    priceLimit = priceLimit,
    minRooms = minRooms,
    minConstructionYear = minConstructionYear,
    maxConstructionYear = maxConstructionYear,
    minArea = minArea,
)
