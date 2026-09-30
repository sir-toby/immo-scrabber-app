package de.immoscrabber.app.properties

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.PropertyType

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
