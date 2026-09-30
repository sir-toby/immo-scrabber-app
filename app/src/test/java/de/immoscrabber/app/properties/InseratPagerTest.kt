package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.InseratPage
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PageCursor
import de.immoscrabber.app.core.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InseratPagerTest {
    private val source = FakePageSource()

    private fun TestScope.pager() = InseratPager(backgroundScope, source::load)

    @Test
    fun `refresh lädt die erste Seite ohne Cursor`() = runTest {
        source.pages[null] = page(ids = 1..20)
        val pager = pager()

        pager.refresh()
        runCurrent()

        val state = pager.state.value
        assertEquals((1..20).map(Int::toString), state.items.map { it.id })
        assertEquals(PageCursor("t20", "20"), state.cursor)
        assertFalse(state.endReached)
        assertEquals(LoadState.Idle, state.loadState)
        assertEquals(listOf<PageCursor?>(null), source.requests)
    }

    @Test
    fun `während refresh läuft, meldet der Pager Laden (Refresh)`() = runTest {
        source.pages[null] = page(ids = 1..20)
        source.gate = CompletableDeferred()
        val pager = pager()

        pager.refresh()
        runCurrent()

        assertEquals(LoadState.Loading(LoadKind.Refresh), pager.state.value.loadState)
    }

    @Test
    fun `loadMore hängt die nächste Seite mit dem Cursor an und erkennt das Ende an einer kurzen Seite`() = runTest {
        source.pages[null] = page(ids = 1..20)
        source.pages[PageCursor("t20", "20")] = page(ids = 21..27)
        val pager = pager()
        pager.refresh()
        runCurrent()

        pager.loadMore()
        runCurrent()

        val state = pager.state.value
        assertEquals((1..27).map(Int::toString), state.items.map { it.id })
        assertTrue(state.endReached)
        assertEquals(null, state.cursor)
        assertEquals(LoadState.Idle, state.loadState)
    }

    @Test
    fun `überlappt die Folgeseite mit geladenen Einträgen, hängt loadMore nur die neuen an`() = runTest {
        // Backend-Fehler immo-scrabber#145 (https://github.com/sir-toby/immo-scrabber/issues/145):
        // `created_at` in Sekunden, Cursor-Vergleich in Mikrosekunden, deshalb wiederholt die
        // Folgeseite alle Inserate derselben Sekunde.
        source.pages[null] = page(ids = 1..20)
        source.pages[PageCursor("t20", "20")] = page(ids = 11..30)
        val pager = pager()
        pager.refresh()
        runCurrent()

        pager.loadMore()
        runCurrent()

        assertEquals((1..30).map(Int::toString), pager.state.value.items.map { it.id })
    }

    @Test
    fun `bringt eine Folgeseite nichts Neues, gilt die Liste als zu Ende statt endlos nachzuladen`() = runTest {
        // Nur eine Schutzregel gegen Endlosladen bei immo-scrabber#145; im Fehlerfall kürzt sie die Liste.
        val cursor = PageCursor("t20", "20")
        source.pages[null] = page(ids = 1..20)
        source.pages[cursor] = page(ids = 1..20)
        val pager = pager()
        pager.refresh()
        runCurrent()

        pager.loadMore()
        runCurrent()
        pager.loadMore()
        runCurrent()

        assertEquals((1..20).map(Int::toString), pager.state.value.items.map { it.id })
        assertTrue(pager.state.value.endReached)
        assertEquals(listOf(null, cursor), source.requests)
    }

    @Test
    fun `refresh übernimmt eine Seite ohne doppelte IDs`() = runTest {
        source.pages[null] = ApiResult.Success(InseratPage(listOf(inserat("1"), inserat("2"), inserat("1")), null))
        val pager = pager()

        pager.refresh()
        runCurrent()

        assertEquals(listOf("1", "2"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `ein Nachladen, das erst nach einem refresh ankommt, wird verworfen`() = runTest {
        // Die Antwort ist schon unterwegs und lässt sich nicht mehr abbrechen.
        val cursor = PageCursor("t20", "20")
        source.pages[null] = page(ids = 1..20)
        source.pages[cursor] = page(ids = 21..40)
        source.nonCancellable = true
        val pager = pager()
        pager.refresh()
        runCurrent()
        source.gate = CompletableDeferred()
        pager.loadMore()
        runCurrent()

        source.pages[null] = page(ids = 100..101)
        pager.refresh()
        source.gate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf("100", "101"), pager.state.value.items.map { it.id })
        assertTrue(pager.state.value.endReached)
        assertEquals(LoadState.Idle, pager.state.value.loadState)
    }

    @Test
    fun `loadMore tut nichts am Ende, während eines Ladevorgangs oder vor dem ersten Laden`() = runTest {
        source.pages[null] = page(ids = 1..5)
        val pager = pager()

        pager.loadMore()
        runCurrent()
        assertEquals(emptyList<PageCursor?>(), source.requests)

        source.gate = CompletableDeferred()
        pager.refresh()
        pager.loadMore()
        runCurrent()
        source.gate!!.complete(Unit)
        runCurrent()
        pager.loadMore()
        runCurrent()

        assertEquals(listOf<PageCursor?>(null), source.requests)
        assertTrue(pager.state.value.endReached)
    }

    @Test
    fun `scheitert das erste Laden, meldet der Pager den Fehler, und retry lädt erneut`() = runTest {
        source.pages[null] = networkError
        val pager = pager()
        pager.refresh()
        runCurrent()

        assertEquals(LoadState.Failed(LoadKind.Refresh, networkError.error), pager.state.value.loadState)
        assertFalse(pager.state.value.loaded)

        source.pages[null] = page(ids = 1..3)
        pager.retry()
        runCurrent()

        assertEquals(listOf("1", "2", "3"), pager.state.value.items.map { it.id })
        assertEquals(LoadState.Idle, pager.state.value.loadState)
    }

    @Test
    fun `scheitert das Nachladen, bleiben die Einträge, automatisches loadMore wartet und retry lädt die Seite`() = runTest {
        val cursor = PageCursor("t20", "20")
        source.pages[null] = page(ids = 1..20)
        source.pages[cursor] = networkError
        val pager = pager()
        pager.refresh()
        runCurrent()
        pager.loadMore()
        runCurrent()

        assertEquals(20, pager.state.value.items.size)
        assertEquals(LoadState.Failed(LoadKind.Append, networkError.error), pager.state.value.loadState)

        pager.loadMore()
        runCurrent()
        assertEquals(listOf(null, cursor), source.requests)

        source.pages[cursor] = page(ids = 21..22)
        pager.retry()
        runCurrent()
        assertEquals(22, pager.state.value.items.size)
        assertEquals(listOf(null, cursor, cursor), source.requests)
    }

    @Test
    fun `refresh verwirft den Cursor, ersetzt die Einträge und bricht laufendes Nachladen ab`() = runTest {
        val cursor = PageCursor("t20", "20")
        source.pages[null] = page(ids = 1..20)
        source.pages[cursor] = page(ids = 21..40)
        val pager = pager()
        pager.refresh()
        runCurrent()
        source.gate = CompletableDeferred()
        pager.loadMore()
        runCurrent()

        source.pages[null] = page(ids = 100..101)
        pager.refresh()
        source.gate!!.complete(Unit)
        runCurrent()

        assertEquals(listOf("100", "101"), pager.state.value.items.map { it.id })
        assertTrue(pager.state.value.endReached)
        assertEquals(LoadState.Idle, pager.state.value.loadState)
    }

    @Test
    fun `scheitert refresh bei vorhandenen Einträgen, bleiben sie stehen`() = runTest {
        source.pages[null] = page(ids = 1..20)
        val pager = pager()
        pager.refresh()
        runCurrent()

        source.pages[null] = networkError
        pager.refresh()
        runCurrent()

        assertEquals(20, pager.state.value.items.size)
        assertEquals(LoadState.Failed(LoadKind.Refresh, networkError.error), pager.state.value.loadState)
    }

    @Test
    fun `remove nimmt einen Eintrag heraus, restore setzt ihn an die alte Stelle zurück`() = runTest {
        source.pages[null] = page(ids = 1..5)
        val pager = pager()
        pager.refresh()
        runCurrent()

        val removed = pager.remove("3")!!
        assertEquals(listOf("1", "2", "4", "5"), pager.state.value.items.map { it.id })

        pager.restore(removed)
        assertEquals(listOf("1", "2", "3", "4", "5"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `remove unbekannter Einträge liefert null, restore verdoppelt nichts und klemmt die Stelle`() = runTest {
        source.pages[null] = page(ids = 1..5)
        val pager = pager()
        pager.refresh()
        runCurrent()

        assertNull(pager.remove("99"))
        val removed = pager.remove("5")!!
        pager.remove("4")
        pager.restore(removed)
        pager.restore(removed)

        assertEquals(listOf("1", "2", "3", "5"), pager.state.value.items.map { it.id })
    }

    @Test
    fun `update ersetzt einen Eintrag an seiner Stelle, der Cursor bleibt`() = runTest {
        source.pages[null] = page(ids = 1..20)
        val pager = pager()
        pager.refresh()
        runCurrent()

        pager.update("2") { it.copy(label = Label.INTERESSANT) }

        val state = pager.state.value
        assertEquals(Label.INTERESSANT, state.items[1].label)
        assertEquals("2", state.items[1].id)
        assertEquals(PageCursor("t20", "20"), state.cursor)
    }
}
