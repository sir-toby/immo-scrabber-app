package de.immoscrabber.app.settings

/**
 * Version in der Zeile „Immo-Finder …“ der Einstellungen: im Debug-Build mit dem Commit, damit
 * Tester Debug-APKs auseinanderhalten („0.0.0-dev (a6ed8ea)“); der Release-Build hat keinen
 * ([gitSha] leer) und zeigt nur die Version („1.1.0“).
 */
fun appVersionLabel(versionName: String, gitSha: String): String =
    if (gitSha.isBlank()) versionName else "$versionName (${gitSha.trim()})"
