package de.immoscrabber.app.core.network

import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import de.immoscrabber.app.core.network.dto.BulkLabelRequestDto
import de.immoscrabber.app.core.network.dto.DeviceRequestDto
import de.immoscrabber.app.core.network.dto.LabelRequestDto
import de.immoscrabber.app.core.network.dto.LoginRequestDto
import de.immoscrabber.app.core.network.dto.SearchPreferenceDto
import de.immoscrabber.app.core.network.dto.TokenPairDto
import de.immoscrabber.app.core.network.dto.toDto
import de.immoscrabber.app.core.network.dto.toInserat
import de.immoscrabber.app.core.network.dto.toSuchprofil

/**
 * Zugriff auf das immo-scrabber-Backend für eine Sitzung. Jede Methode liefert ein
 * [ApiResult] und wirft nie; Fehler sind bereits in [ApiError] übersetzt.
 * Erzeugt über [ApiClientFactory].
 */
class ImmoApi internal constructor(private val service: ImmoApiService) {

    /**
     * `POST /auth/login`. Nur 401 heißt Zugangsdaten falsch ([ApiError.SessionExpired]); ohne Bearer
     * ist 422 kein JWT-Fehler und kommt als [ApiError.Http] an (Entscheidung #7). Fehlt eines der
     * Tokens, ist das [ApiError.InvalidResponse].
     */
    suspend fun login(username: String, password: String): ApiResult<TokenPair> = apiCall(LOGIN_REJECTED_CODES) {
        service.login(LoginRequestDto(username, password)).toTokenPair()
    }

    /**
     * `POST /auth/register` (#12). 201 ist Erfolg; ein vergebener Benutzername kommt als
     * [ApiError.Http] mit 409. Ohne Bearer bedeutet kein Status Sitzungsende.
     */
    suspend fun register(username: String, password: String): ApiResult<Unit> = apiCall(sessionExpiredCodes = emptySet()) {
        service.register(LoginRequestDto(username, password))
    }

    /**
     * `POST /auth/refresh` mit dem Refresh-Token als Bearer; liefert ein neues Paar (Rotation).
     * 401/422 bedeuten hier wirklich Sitzungsende (Entscheidung #7).
     */
    suspend fun refresh(refreshToken: String): ApiResult<TokenPair> = apiCall {
        service.refresh(bearer(refreshToken)).toTokenPair()
    }

    /**
     * `GET /properties/results` für genau einen Immobilientyp (dann ist der Cursor eindeutig).
     * [label] `null` heißt „Alle“; [Label.UNBEWERTET] ist der Filter „Neu“. Ohne jedes
     * Suchprofil kommt [ApiError.BadRequest] („Keine Suchparameter gefunden“).
     * Die API kennt kein `hasMore`: Eine volle Seite liefert einen [InseratPage.nextCursor].
     */
    suspend fun inserate(
        type: PropertyType,
        label: Label?,
        cursor: PageCursor? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): ApiResult<InseratPage> = apiCall {
        val dtos = service.results(
            propertyTypes = type.queryValue,
            pageSize = pageSize,
            label = label?.apiValue,
            beforeCreatedAt = cursor?.createdAt,
            beforeId = cursor?.id,
        ).listFor(type)
        val inserate = dtos.map { it.toInserat(it.id.required("id"), type) }
        val last = inserate.lastOrNull()
        val nextCursor = if (inserate.size >= pageSize && last?.createdAt != null) {
            PageCursor(last.createdAt, last.id)
        } else {
            null
        }
        InseratPage(inserate, nextCursor)
    }

    /** `PATCH /properties/<type>/<id>/label`; idempotent. */
    suspend fun bewerten(type: PropertyType, id: String, label: Label): ApiResult<Unit> = apiCall {
        service.label(type.apiValue, id, LabelRequestDto(label.apiValue))
        Unit
    }

    /**
     * `PATCH /properties/labels`: bewertet genau die „Neu“-Liste des Typs (unbewertet und
     * passend zu den Suchprofilen). Liefert die Anzahl (`updated`).
     */
    suspend fun alleNeuenBewerten(type: PropertyType, label: Label): ApiResult<Int> = apiCall {
        service.labelAllNew(BulkLabelRequestDto(type.apiValue, label.apiValue)).updated.required("updated")
    }

    /** `GET /preferences`: alle Suchprofile des Nutzers. */
    suspend fun suchprofile(): ApiResult<List<Suchprofil>> = apiCall {
        service.preferences().map { it.toSuchprofil() }
    }

    /** `POST /preferences`. Über 10 Profile: [ApiError.BadRequest] mit Servertext. */
    suspend fun suchprofilAnlegen(input: SuchprofilInput): ApiResult<Suchprofil> = apiCall {
        service.createPreference(input.toDto()).toSuchprofil()
    }

    /** `PATCH /preferences/<uuid>` mit allen Feldern aus [input]. */
    suspend fun suchprofilAendern(id: String, input: SuchprofilInput): ApiResult<Suchprofil> = apiCall {
        service.updatePreference(id, input.toDto()).toSuchprofil()
    }

    /** `DELETE /preferences/<uuid>`. */
    suspend fun suchprofilLoeschen(id: String): ApiResult<Unit> = apiCall {
        service.deletePreference(id)
        Unit
    }

    /** `PUT /devices`: meldet das FCM-Token dieser Installation für Benachrichtigungen an. */
    suspend fun geraetRegistrieren(fcmToken: String): ApiResult<Unit> = apiCall {
        service.registerDevice(DeviceRequestDto(fcmToken))
    }

    /** `DELETE /devices/<token>`: meldet das FCM-Token ab (204, auch wenn unbekannt). */
    suspend fun geraetAbmelden(fcmToken: String): ApiResult<Unit> = apiCall {
        service.unregisterDevice(fcmToken)
    }
}

private fun SearchPreferenceDto.toSuchprofil() = toSuchprofil(uuid.required("uuid"))

private fun TokenPairDto.toTokenPair() =
    TokenPair(accessToken.required("access_token"), refreshToken.required("refresh_token"))

/** Seitengröße der Listen (Entscheidung #6). */
const val DEFAULT_PAGE_SIZE = 20

/** Access- und Refresh-Token aus Login oder Refresh. */
data class TokenPair(val accessToken: String, val refreshToken: String)

/** Beim Login heißt nur 401 „Benutzername oder Passwort falsch“. */
private val LOGIN_REJECTED_CODES = setOf(401)
