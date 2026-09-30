package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.model.Suchprofil
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
 * Der Editor (#32) lädt nach jedem Speichern/Löschen per [laden] neu, statt lokal zusammenzubauen.
 */
interface SuchprofilRepository {
    /** Zuletzt geladene Suchprofile in Server-Reihenfolge; `null`, solange nie erfolgreich geladen. */
    val suchprofile: StateFlow<List<Suchprofil>?>

    /** Holt `GET /preferences`; bei Erfolg ersetzt das Ergebnis [suchprofile], sonst bleibt die alte Liste. */
    suspend fun laden(): ApiResult<List<Suchprofil>>
}

class ApiSuchprofilRepository(private val api: ImmoApi) : SuchprofilRepository {
    private val _suchprofile = MutableStateFlow<List<Suchprofil>?>(null)
    override val suchprofile: StateFlow<List<Suchprofil>?> = _suchprofile.asStateFlow()

    override suspend fun laden(): ApiResult<List<Suchprofil>> =
        api.suchprofile().also { if (it is ApiResult.Success) _suchprofile.value = it.value }
}
