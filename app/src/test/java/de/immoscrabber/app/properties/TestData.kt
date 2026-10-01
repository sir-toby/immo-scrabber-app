package de.immoscrabber.app.properties

import androidx.lifecycle.SavedStateHandle
import de.immoscrabber.app.core.data.InseratRepository
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.data.VeraltetMerker
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.model.Suchprofil
import de.immoscrabber.app.core.model.SuchprofilInput
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration

/** Ein Häuser-Tab mit frischem Veraltet-Merker und leerem gespeicherten Zustand (Kaltstart). */
fun testViewModel(
    repository: InseratRepository,
    veraltet: VeraltetMerker = VeraltetMerker(now = { Duration.ZERO }, threshold = Duration.INFINITE),
    savedState: SavedStateHandle = SavedStateHandle(),
    type: PropertyType = PropertyType.HOUSE,
    suchprofile: SuchprofilRepository = FakeSuchprofile(PropertyType.entries.map(::suchprofil)),
) = PropertyListViewModel(type, repository, suchprofile, veraltet, savedState)

fun suchprofil(type: PropertyType) = Suchprofil(
    id = type.apiValue,
    propertyType = type,
    city = "Erlangen",
    zipCode = "91054",
    radius = 20,
    ausgeschlosseneAnbieter = emptyList(),
    excludedSources = emptyList(),
    priceLimit = null,
    minRooms = null,
    minConstructionYear = null,
    maxConstructionYear = null,
    minArea = null,
)

/**
 * Suchprofile der Sitzung: [suchprofile] ist der Speicher (`null` = nie geladen),
 * [laden] liefert [ladenResult] und zählt die Aufrufe.
 */
class FakeSuchprofile(initial: List<Suchprofil>? = null) : SuchprofilRepository {
    override val suchprofile = MutableStateFlow(initial)
    var ladenResult: ApiResult<List<Suchprofil>> = ApiResult.Success(emptyList())
    var ladenCalls = 0

    override suspend fun laden(): ApiResult<List<Suchprofil>> {
        ladenCalls++
        return ladenResult.also { if (it is ApiResult.Success) suchprofile.value = it.value }
    }

    override suspend fun speichern(id: String?, input: SuchprofilInput) = error("nicht im Tab")

    override suspend fun loeschen(profil: Suchprofil) = error("nicht im Tab")
}

fun inserat(
    id: String,
    label: Label = Label.UNBEWERTET,
    type: PropertyType = PropertyType.HOUSE,
    price: Double? = null,
    zipCode: String? = null,
    city: String? = null,
    rooms: Double? = null,
    livingArea: Double? = null,
    plotArea: Double? = null,
    constructionYear: Int? = null,
    url: String? = null,
) = Inserat(
    id = id,
    propertyType = type,
    title = "Inserat $id",
    imageUrl = null,
    price = price,
    zipCode = zipCode,
    city = city,
    street = null,
    houseNumber = null,
    rooms = rooms,
    livingArea = livingArea,
    plotArea = plotArea,
    anbieter = null,
    url = url,
    source = null,
    createdAt = "t$id",
    label = label,
    constructionYear = constructionYear,
    energyEfficiencyClass = null,
)

/** Eine Seite wie vom Server: bei 20 Einträgen mit Cursor auf den letzten, sonst Ende. */
fun page(ids: IntRange, label: Label = Label.UNBEWERTET): ApiResult<InseratPage> {
    val inserate = ids.map { inserat(it.toString(), label) }
    val last = inserate.lastOrNull()
    val cursor = if (inserate.size >= 20 && last != null) PageCursor(last.createdAt!!, last.id) else null
    return ApiResult.Success(InseratPage(inserate, cursor))
}

val networkError = ApiResult.Failure(ApiError.Network(IOException("offline")))

/** Seiten je Cursor; mit [gate] lässt sich eine Antwort zurückhalten. */
class FakePageSource {
    val pages = mutableMapOf<PageCursor?, ApiResult<InseratPage>>()
    val requests = mutableListOf<PageCursor?>()
    var gate: CompletableDeferred<Unit>? = null

    /** Antworten kommen auch nach einem Abbruch noch an (etwa schon unterwegs). */
    var nonCancellable = false

    suspend fun load(cursor: PageCursor?): ApiResult<InseratPage> {
        requests += cursor
        val gate = gate
        if (nonCancellable) withContext(NonCancellable) { gate?.await() } else gate?.await()
        return pages[cursor] ?: error("keine Seite für $cursor")
    }
}
