package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.network.ApiError
import de.immoscrabber.app.core.network.ApiResult
import de.immoscrabber.app.core.network.MockApiRule
import de.immoscrabber.app.core.network.jsonResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SuchprofilRepositoryTest {
    @get:Rule val mock = MockApiRule()

    private val repository by lazy { ApiSuchprofilRepository(mock.api) }

    @Test
    fun `vor dem ersten Laden ist nichts im Speicher`() {
        assertNull(repository.suchprofile.value)
    }

    @Test
    fun `Laden holt GET preferences und behält die Liste im Speicher`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))

        val result = repository.laden()

        assertEquals("/api/preferences", mock.takeRequest().path)
        assertTrue(result is ApiResult.Success)
        assertEquals(listOf("Erlangen", "Erlangen", "Erlangen"), repository.suchprofile.value?.map { it.city })
    }

    @Test
    fun `gescheitertes Neuladen lässt die alte Liste stehen`() = runTest {
        mock.enqueue(jsonResponse(200, "preferences/preferences_list.json"))
        repository.laden()
        mock.enqueue(MockResponse().setResponseCode(503))

        val result = repository.laden()

        assertEquals(ApiResult.Failure(ApiError.Http(503)), result)
        assertEquals(3, repository.suchprofile.value?.size)
    }
}
