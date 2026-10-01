package de.immoscrabber.app.core.network

import de.immoscrabber.app.core.network.dto.BulkLabelRequestDto
import de.immoscrabber.app.core.network.dto.BulkLabelResponseDto
import de.immoscrabber.app.core.network.dto.DeviceRequestDto
import de.immoscrabber.app.core.network.dto.LabelRequestDto
import de.immoscrabber.app.core.network.dto.LabelResponseDto
import de.immoscrabber.app.core.network.dto.LoginRequestDto
import de.immoscrabber.app.core.network.dto.MessageDto
import de.immoscrabber.app.core.network.dto.ResultsDto
import de.immoscrabber.app.core.network.dto.SearchPreferenceDto
import de.immoscrabber.app.core.network.dto.SearchPreferenceRequestDto
import de.immoscrabber.app.core.network.dto.TokenPairDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit-Beschreibung der Backend-Endpoints (Pfade relativ zur Basis-URL `…/api/`). */
internal interface ImmoApiService {
    @Unauthenticated
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenPairDto

    /** 201 mit `{}`; der Body wird ignoriert. */
    @Unauthenticated
    @POST("auth/register")
    suspend fun register(@Body body: LoginRequestDto)

    /** `authorization` ist `Bearer <refresh_token>`; der Interceptor lässt ihn unverändert. */
    @POST("auth/refresh")
    suspend fun refresh(@Header(AUTHORIZATION) authorization: String): TokenPairDto

    /** `propertyTypes` im Plural; `label` weglassen heißt „alle“; Cursor aus dem letzten Eintrag. */
    @GET("properties/results")
    suspend fun results(
        @Query("propertyTypes") propertyTypes: String,
        @Query("pageSize") pageSize: Int,
        @Query("label") label: String?,
        @Query("beforeCreatedAt") beforeCreatedAt: String?,
        @Query("beforeId") beforeId: String?,
    ): ResultsDto

    /** `type` im Singular (`house`/`flat`/`site`). */
    @PATCH("properties/{type}/{id}/label")
    suspend fun label(
        @Path("type") type: String,
        @Path("id") id: String,
        @Body body: LabelRequestDto,
    ): LabelResponseDto

    @PATCH("properties/labels")
    suspend fun labelAllNew(@Body body: BulkLabelRequestDto): BulkLabelResponseDto

    @GET("preferences")
    suspend fun preferences(): List<SearchPreferenceDto>

    /** Geocodiert synchron beim Server, daher der großzügige Read-Timeout. */
    @POST("preferences")
    suspend fun createPreference(@Body body: SearchPreferenceRequestDto): SearchPreferenceDto

    @PATCH("preferences/{uuid}")
    suspend fun updatePreference(
        @Path("uuid") uuid: String,
        @Body body: SearchPreferenceRequestDto,
    ): SearchPreferenceDto

    @DELETE("preferences/{uuid}")
    suspend fun deletePreference(@Path("uuid") uuid: String): MessageDto

    /** 204 ohne Body. */
    @PUT("devices")
    suspend fun registerDevice(@Body body: DeviceRequestDto)

    /** 204 ohne Body, auch wenn das Token unbekannt war. */
    @DELETE("devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String)
}

/** Markiert Endpoints, an die der [BearerTokenInterceptor] kein Access-Token hängt. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Unauthenticated
