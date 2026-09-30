package de.immoscrabber.app.core.network.dto

import kotlinx.serialization.Serializable

/** Body für `PUT /devices` (FCM-Token der Installation). */
@Serializable
internal data class DeviceRequestDto(val token: String)
