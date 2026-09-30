package de.immoscrabber.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.ui.graphics.vector.ImageVector
import de.immoscrabber.app.core.model.PropertyType

/**
 * Icon des Immobilientyps: Bottom Navigation, Bild-Platzhalter und Suchprofil-Liste. Liegt in
 * `core`, weil `properties` und `settings` es beide brauchen.
 */
val PropertyType.icon: ImageVector
    get() = when (this) {
        PropertyType.HOUSE -> Icons.Outlined.House
        PropertyType.FLAT -> Icons.Outlined.Apartment
        PropertyType.SITE -> Icons.Outlined.Landscape
    }
