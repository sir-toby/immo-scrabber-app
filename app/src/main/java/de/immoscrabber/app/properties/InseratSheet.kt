package de.immoscrabber.app.properties

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.ui.theme.immoColors
import java.time.Clock

/**
 * Detail-Sheet eines Inserats der Wischliste (#14, Variante B „Kompakt + Status“): Kopf mit Bild,
 * Preis, vollem Titel und Adresse; Eckdaten als Chips mit Symbol; Herkunft; Umschalter
 * Neu / Favorit / Archiv; „Inserat öffnen“ und „Teilen“ (nur mit Link).
 *
 * Das Sheet liegt in einem eigenen Fenster über der Scaffold-Snackbar, deshalb zeigt es die
 * Snackbar der Bewertung (mit „Rückgängig“) selbst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InseratSheet(
    inserat: Inserat,
    sheetState: SheetState,
    snackbarHostState: SnackbarHostState,
    onBewerten: (Label) -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clock = remember { Clock.systemDefaultZone() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(inserat)
                Facts(inserat)
                Origin(inserat, clock)
                Bewertung(inserat.label, onBewerten)
                Actions(inserat, onOpen)
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Header(inserat: Inserat) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InseratImage(inserat, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatPrice(inserat.price),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                inserat.title?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        val address = formatAddress(inserat)
        if (address.isNotEmpty()) IconLine(Icons.Outlined.Place, address, iconDescription = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(inserat: Inserat) {
    val facts = detailFacts(inserat)
    val energyColor = if (inserat.propertyType.hasEnergyClass) {
        MaterialTheme.immoColors.energyClass(inserat.energyEfficiencyClass)
    } else {
        null
    }
    if (facts.isEmpty() && energyColor == null) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { fact -> FactChip(fact.kind.icon) { Text(fact.text, style = MaterialTheme.typography.labelLarge) } }
        if (energyColor != null) {
            val description = stringResource(R.string.energy_class)
            FactChip(Icons.Outlined.Bolt, Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
                EnergyClassBadge(inserat.energyEfficiencyClass)
            }
        }
    }
}

private val FactKind.icon: ImageVector
    get() = when (this) {
        FactKind.ROOMS -> Icons.Outlined.MeetingRoom
        FactKind.LIVING_AREA -> Icons.Outlined.SquareFoot
        FactKind.PLOT_AREA -> Icons.Outlined.Landscape
        FactKind.CONSTRUCTION_YEAR -> Icons.Outlined.CalendarMonth
        FactKind.PRICE_PER_SQUARE_METER -> Icons.Outlined.Euro
    }

@Composable
private fun FactChip(icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Origin(inserat: Inserat, clock: Clock) {
    val gefunden = formatGefunden(inserat.createdAt, clock)
    val anbieter = inserat.anbieter?.trim()?.takeIf { it.isNotEmpty() && inserat.propertyType != PropertyType.SITE }
    val source = inserat.source?.trim()?.takeIf(String::isNotEmpty)
    if (gefunden == null && anbieter == null && source == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        gefunden?.let { IconLine(Icons.Outlined.Schedule, it, iconDescription = null) }
        anbieter?.let { IconLine(Icons.Outlined.Business, it, stringResource(R.string.anbieter)) }
        source?.let { IconLine(Icons.Outlined.Language, it, stringResource(R.string.source)) }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String, iconDescription: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = iconDescription, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bewertung(current: Label, onBewerten: (Label) -> Unit) {
    val description = stringResource(R.string.rating_segments)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        bewertungsSegmente.forEachIndexed { index, label ->
            SegmentedButton(
                selected = label == current,
                onClick = { segmentWechsel(current, label)?.let(onBewerten) },
                shape = SegmentedButtonDefaults.itemShape(index, bewertungsSegmente.size),
                label = { Text(stringResource(label.segmentTitle)) },
            )
        }
    }
}

private val Label.segmentTitle: Int
    get() = when (this) {
        Label.UNBEWERTET -> R.string.segment_new
        Label.INTERESSANT -> R.string.segment_favorite
        Label.UNINTERESSANT -> R.string.segment_archive
    }

@Composable
private fun Actions(inserat: Inserat, onOpen: () -> Unit) {
    val share = shareContent(inserat) ?: return
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.open_listing), Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = { context.share(share) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.share), Modifier.padding(start = 8.dp))
        }
    }
}

/** Android-Teilen-Menü mit dem Link als Text und dem Titel als Betreff. */
private fun Context.share(content: ShareContent) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content.text)
        content.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    // Den Systemdialog zum Teilen gibt es immer; ohne passende App zeigt er das selbst.
    startActivity(Intent.createChooser(send, null))
}
