package de.immoscrabber.build

// Pure Kotlin (no Gradle API): this file is also compiled into the app's unit tests.

/** versionName/versionCode of the app, derived from a release tag `vX.Y.Z` (decision #19). */
data class AppVersion(val name: String, val code: Int) {
    companion object {
        private val DEV = AppVersion(name = "0.0.0-dev", code = 1)
        private val TAG = Regex("""v(\d+)\.(\d+)\.(\d+)""")

        /** `v1.2.0` → `1.2.0` / `10200`; no tag → `0.0.0-dev` / `1`; anything else fails the build. */
        fun fromTag(tag: String?): AppVersion {
            if (tag.isNullOrBlank()) return DEV
            val match = requireNotNull(TAG.matchEntire(tag.trim())) {
                "VERSION_TAG '$tag' hat nicht die Form vX.Y.Z"
            }
            val (major, minor, patch) = match.destructured.toList().map(String::toInt)
            require(minor < 100 && patch < 100) {
                "VERSION_TAG '$tag': Minor und Patch müssen < 100 sein (versionCode = major·10000 + minor·100 + patch)"
            }
            return AppVersion(name = "$major.$minor.$patch", code = major * 10000 + minor * 100 + patch)
        }
    }
}
