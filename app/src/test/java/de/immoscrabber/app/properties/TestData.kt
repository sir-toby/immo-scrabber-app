package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

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
    provider = null,
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

    suspend fun load(cursor: PageCursor?): ApiResult<InseratPage> {
        requests += cursor
        gate?.await()
        return pages[cursor] ?: error("keine Seite für $cursor")
    }
}
