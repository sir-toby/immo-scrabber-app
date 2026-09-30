package de.immoscrabber.app.properties

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.ui.theme.immoColors

/*
 * Wie ein Label aussieht und in welche Richtung man dafür wischt, an einer Stelle für Badge,
 * Wischliste und Kartenstapel (Entscheidungen #3, #6).
 */

/** Label eines Wischs: nach rechts interessant, nach links uninteressant. */
internal fun swipeLabel(rightward: Boolean): Label = if (rightward) Label.INTERESSANT else Label.UNINTERESSANT

/** Icon eines bewerteten Inserats; unbewertet hat keins. */
internal val Label.icon: ImageVector?
    get() = when (this) {
        Label.INTERESSANT -> Icons.Filled.Favorite
        Label.UNINTERESSANT -> Icons.Outlined.Archive
        Label.UNBEWERTET -> null
    }

/** Fachfarbe des Labels: grün für interessant, rot für uninteressant. */
internal val Label.color: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        Label.INTERESSANT -> MaterialTheme.immoColors.interessant
        Label.UNINTERESSANT -> MaterialTheme.immoColors.uninteressant
        Label.UNBEWERTET -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** Inhaltsfarbe auf einer Fläche in [color]. */
internal val Label.onColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        Label.INTERESSANT -> MaterialTheme.immoColors.onInteressant
        Label.UNINTERESSANT -> MaterialTheme.immoColors.onUninteressant
        Label.UNBEWERTET -> MaterialTheme.colorScheme.surface
    }
