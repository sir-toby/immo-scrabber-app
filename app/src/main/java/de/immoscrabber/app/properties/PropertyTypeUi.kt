package de.immoscrabber.app.properties

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.ui.graphics.vector.ImageVector
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.PropertyType

/** Icon des Immobilientyps: Bottom Navigation und Bild-Platzhalter. */
val PropertyType.icon: ImageVector
    get() = when (this) {
        PropertyType.HOUSE -> Icons.Outlined.House
        PropertyType.FLAT -> Icons.Outlined.Apartment
        PropertyType.SITE -> Icons.Outlined.Landscape
    }

/** Typname im Plural („Häuser“): Tab-Titel und Leerzustände. */
@get:StringRes
val PropertyType.pluralName: Int
    get() = when (this) {
        PropertyType.HOUSE -> R.string.tab_houses
        PropertyType.FLAT -> R.string.tab_flats
        PropertyType.SITE -> R.string.tab_sites
    }

/** „37 Häuser ins Archiv verschoben“ nach „Alle als uninteressant markieren“. */
@get:PluralsRes
val PropertyType.archivedAllMessage: Int
    get() = when (this) {
        PropertyType.HOUSE -> R.plurals.archived_all_houses
        PropertyType.FLAT -> R.plurals.archived_all_flats
        PropertyType.SITE -> R.plurals.archived_all_sites
    }

/** Energieklasse gibt es nur bei Häusern und Wohnungen. */
val PropertyType.hasEnergyClass: Boolean get() = this != PropertyType.SITE
