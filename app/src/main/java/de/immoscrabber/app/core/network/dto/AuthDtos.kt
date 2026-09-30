package de.immoscrabber.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequestDto(
    val username: String,
    val password: String,
)

/** Antwort von `POST /auth/login` und `POST /auth/refresh`. */
@Serializable
internal data class TokenPairDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)
