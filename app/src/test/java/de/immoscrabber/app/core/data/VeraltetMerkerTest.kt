package de.immoscrabber.app.core.data

import de.immoscrabber.app.core.model.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class VeraltetMerkerTest {
    private var now = Duration.ZERO
    private val merker = VeraltetMerker(now = { now }, threshold = 30.minutes)

    private fun vergeht(dauer: Duration) {
        now += dauer
    }

    @Test
    fun `anfangs ist nichts veraltet`() {
        assertEquals(emptySet<PropertyType>(), merker.stale.value)
        PropertyType.entries.forEach { assertFalse(merker.consume(it)) }
    }

    @Test
    fun `markStale markiert nur den einen Typ`() {
        merker.markStale(PropertyType.FLAT)

        assertEquals(setOf(PropertyType.FLAT), merker.stale.value)
    }

    @Test
    fun `consume liefert den Merker genau einmal und lässt die anderen Typen stehen`() {
        merker.markStale(PropertyType.HOUSE)
        merker.markStale(PropertyType.SITE)

        assertTrue(merker.consume(PropertyType.HOUSE))
        assertFalse(merker.consume(PropertyType.HOUSE))
        assertEquals(setOf(PropertyType.SITE), merker.stale.value)
    }

    @Test
    fun `mehr als 30 Minuten im Hintergrund markieren alle Typen`() {
        merker.onBackground()
        vergeht(30.minutes + 1.milliseconds)
        merker.onForeground()

        assertEquals(PropertyType.entries.toSet(), merker.stale.value)
    }

    @Test
    fun `genau 30 Minuten im Hintergrund markieren nichts`() {
        merker.onBackground()
        vergeht(30.minutes)
        merker.onForeground()

        assertEquals(emptySet<PropertyType>(), merker.stale.value)
    }

    @Test
    fun `gezählt wird jeder Aufenthalt im Hintergrund für sich`() {
        merker.onBackground()
        vergeht(20.minutes)
        merker.onForeground()
        vergeht(5.minutes)
        merker.onBackground()
        vergeht(20.minutes)
        merker.onForeground()

        assertEquals(emptySet<PropertyType>(), merker.stale.value)
    }

    @Test
    fun `Vordergrund ohne vorherigen Hintergrund markiert nichts`() {
        vergeht(60.minutes)
        merker.onForeground()

        assertEquals(emptySet<PropertyType>(), merker.stale.value)
    }

    @Test
    fun `ein zweites Vordergrund-Signal zählt nicht noch einmal`() {
        merker.onBackground()
        vergeht(31.minutes)
        merker.onForeground()
        PropertyType.entries.forEach { merker.consume(it) }

        vergeht(60.minutes)
        merker.onForeground()

        assertEquals(emptySet<PropertyType>(), merker.stale.value)
    }

    @Test
    fun `die Schwelle ist einstellbar (Debug-Build)`() {
        val kurz = VeraltetMerker(now = { now }, threshold = 1.minutes)

        kurz.onBackground()
        vergeht(2.minutes)
        kurz.onForeground()

        assertEquals(PropertyType.entries.toSet(), kurz.stale.value)
    }
}
