package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.ImmoApi

/**
 * Inserate lesen und bewerten, für genau eine Sitzung (hängt an `Session.inserate` und wird mit
 * ihr verworfen, Entscheidung #10). Ein dünner Durchgriff auf das [ImmoApi]; wann neu geladen
 * wird, entscheidet der [VeraltetMerker].
 */
interface InseratRepository {
    /** Eine Seite für [type]; [label] `null` heißt „Alle“, [Label.UNBEWERTET] ist „Neu“. */
    suspend fun seite(type: PropertyType, label: Label?, cursor: PageCursor?): ApiResult<InseratPage>

    /** Setzt das Label eines Inserats (`PATCH /properties/<type>/<id>/label`). */
    suspend fun bewerten(type: PropertyType, id: String, label: Label): ApiResult<Unit>

    /**
     * Bewertet die ganze „Neu“-Liste von [type] auf einmal (`PATCH /properties/labels`);
     * liefert die Anzahl der geänderten Inserate.
     */
    suspend fun alleNeuenBewerten(type: PropertyType, label: Label): ApiResult<Int>
}

class ApiInseratRepository(private val api: ImmoApi) : InseratRepository {
    override suspend fun seite(type: PropertyType, label: Label?, cursor: PageCursor?) =
        api.inserate(type, label, cursor)

    override suspend fun bewerten(type: PropertyType, id: String, label: Label) =
        api.bewerten(type, id, label)

    override suspend fun alleNeuenBewerten(type: PropertyType, label: Label) =
        api.alleNeuenBewerten(type, label)
}
