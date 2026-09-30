package de.immoscrabber.app.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme
import de.immoscrabber.app.core.ui.theme.immoColors

/**
 * Zeile der Wischliste (Entscheidung #6): Thumbnail, Preis, Titel (einzeilig), „PLZ Ort ·
 * Eckdaten“, darunter Quelle und Energieklasse; mit [showLabel] (nur „Alle“) das Label-Badge.
 */
@Composable
fun InseratRow(inserat: Inserat, showLabel: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InseratImage(inserat, Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = formatPrice(inserat.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            inserat.title?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val placeAndFacts = formatPlaceAndFacts(inserat)
            if (placeAndFacts.isNotEmpty()) {
                Text(
                    text = placeAndFacts,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                inserat.source?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (inserat.propertyType.hasEnergyClass) EnergyClassBadge(inserat.energyEfficiencyClass)
                if (showLabel) {
                    Spacer(Modifier.weight(1f))
                    LabelBadge(inserat.label)
                }
            }
        }
    }
}

/** Bild oder grauer Platzhalter mit Typ-Icon (ohne Bild, bei unbrauchbarer URL, bei Ladefehler); für Zeile und Karte. */
@Composable
internal fun InseratImage(inserat: Inserat, modifier: Modifier, iconSize: Dp = 36.dp) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = inserat.propertyType.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        // Lädt das Bild nicht, bleibt es transparent und der Platzhalter darunter sichtbar.
        thumbnailUrl(inserat.imageUrl)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun EnergyClassBadge(energyClass: String?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.immoColors.energyClass(energyClass) ?: return
    Text(
        text = energyClass!!.trim().uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.immoColors.onEnergyClass,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** „♡ Favorit“ bzw. „Archiv“; unbewertet ohne Badge. */
@Composable
private fun LabelBadge(label: Label) {
    val (icon, text, color) = when (label) {
        Label.INTERESSANT -> Triple(Icons.Filled.Favorite, R.string.badge_favorite, MaterialTheme.immoColors.interessant)
        Label.UNINTERESSANT -> Triple(Icons.Outlined.Archive, R.string.badge_archive, MaterialTheme.immoColors.uninteressant)
        Label.UNBEWERTET -> return
    }
    BadgePill(icon, stringResource(text), color)
}

@Composable
private fun BadgePill(icon: ImageVector, text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), contentColor = color) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Preview
@Composable
private fun InseratRowPreview() {
    ImmoFinderTheme {
        Column {
            InseratRow(
                Inserat(
                    id = "1", propertyType = PropertyType.HOUSE,
                    title = "Einfamilienhaus mit Garten – ruhige Lage, viel Platz für die Familie",
                    imageUrl = null, price = 450_000.0, zipCode = "91054", city = "Erlangen",
                    street = null, houseNumber = null, rooms = 5.0, livingArea = 140.0, plotArea = 600.0,
                    provider = null, url = null, source = "Kleinanzeigen", createdAt = null,
                    label = Label.INTERESSANT, constructionYear = 1978, energyEfficiencyClass = "B",
                ),
                showLabel = true,
                onClick = {},
            )
        }
    }
}
