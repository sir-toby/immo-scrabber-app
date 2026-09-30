package de.immoscrabber.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the build-logic helper in `buildSrc/src/main/kotlin` (compiled into the app's unit tests,
 * see `app/build.gradle.kts`), so `testDebugUnitTest` covers it.
 */
class AppVersionTest {

    @Test
    fun `release tag yields version name and code`() {
        assertEquals(AppVersion(name = "1.2.0", code = 10200), AppVersion.fromTag("v1.2.0"))
    }

    @Test
    fun `code is major times 10000 plus minor times 100 plus patch`() {
        assertEquals(AppVersion(name = "3.14.15", code = 31415), AppVersion.fromTag("v3.14.15"))
    }

    @Test
    fun `missing tag yields dev version`() {
        assertEquals(AppVersion(name = "0.0.0-dev", code = 1), AppVersion.fromTag(null))
        assertEquals(AppVersion(name = "0.0.0-dev", code = 1), AppVersion.fromTag("  "))
    }

    @Test
    fun `malformed tag is rejected`() {
        for (tag in listOf("1.2.0", "v1.2", "v1.2.0-beta", "vX.Y.Z")) {
            assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag(tag) }
        }
    }

    @Test
    fun `minor or patch of 100 or more is rejected because it would collide in the code`() {
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("v1.100.0") }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("v1.0.100") }
    }
}
