package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.ImmoApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mehr Suchprofile nimmt das Backend nicht an (400 mit Servertext). */
const val MAX_SUCHPROFILE = 10

/**
 * Die Suchprofile einer Sitzung (hängt an `Session.suchprofile` und wird mit ihr verworfen,
 * Entscheidung #10): beim ersten Öffnen der Einstellungen geladen, danach im Speicher.
 * Nach jedem Speichern/Löschen lädt es von `GET /preferences` neu, statt lokal zusammenzubauen
 * (das Backend normalisiert und geocodiert).
 */
interface SuchprofilRepository {
    /** Zuletzt geladene Suchprofile in Server-Reihenfolge; `null`, solange nie erfolgreich geladen. */
    val suchprofile: StateFlow<List<Suchprofil>?>

    /** Holt `GET /preferences`; bei Erfolg ersetzt das Ergebnis [suchprofile], sonst bleibt die alte Liste. */
    suspend fun laden(): ApiResult<List<Suchprofil>>

    /**
     * Legt an (`id == null`, `POST`) oder ändert (`PATCH`). Bei Erfolg ist die Liste des Typs
     * veraltet und [suchprofile] wird neu geladen; scheitert nur das Neuladen, gilt das Speichern
     * trotzdem als erfolgreich.
     */
    suspend fun speichern(id: String?, input: SuchprofilInput): ApiResult<Unit>

    /** `DELETE`; danach wie [speichern]: Typ veraltet, Liste neu geladen. */
    suspend fun loeschen(profil: Suchprofil): ApiResult<Unit>
}

/**
 * @param veraltet bekommt nach jedem Speichern/Löschen den Typ des Profils (Entscheidung #10).
 *   Geht die Zahl der Suchprofile von 0 weg oder auf 0, sind alle Typen veraltet: Ohne jedes
 *   Suchprofil zeigen alle drei Tabs „Noch kein Suchprofil“ (400), danach nicht mehr, und umgekehrt.
 */
class ApiSuchprofilRepository(
    private val api: ImmoApi,
    private val veraltet: VeraltetMerker,
) : SuchprofilRepository {
    private val _suchprofile = MutableStateFlow<List<Suchprofil>?>(null)
    override val suchprofile: StateFlow<List<Suchprofil>?> = _suchprofile.asStateFlow()

    override suspend fun laden(): ApiResult<List<Suchprofil>> =
        api.suchprofile().also { if (it is ApiResult.Success) _suchprofile.value = it.value }

    override suspend fun speichern(id: String?, input: SuchprofilInput): ApiResult<Unit> {
        val result = if (id == null) api.suchprofilAnlegen(input) else api.suchprofilAendern(id, input)
        return nachAenderung(result, input.propertyType)
    }

    override suspend fun loeschen(profil: Suchprofil): ApiResult<Unit> =
        nachAenderung(api.suchprofilLoeschen(profil.id), profil.propertyType)

    private suspend fun nachAenderung(result: ApiResult<*>, type: PropertyType?): ApiResult<Unit> {
        if (result is ApiResult.Failure) return result
        val vorher = _suchprofile.value
        val nachher = (laden() as? ApiResult.Success)?.value
        val keinProfilVorherOderNachher = vorher.isNullOrEmpty() || nachher?.isEmpty() == true
        if (type == null || keinProfilVorherOderNachher) {
            PropertyType.entries.forEach(veraltet::markStale)
        } else {
            veraltet.markStale(type)
        }
        return ApiResult.Success(Unit)
    }
}
