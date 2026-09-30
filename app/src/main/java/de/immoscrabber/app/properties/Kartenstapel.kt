package de.immoscrabber.app.properties

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Karten, die sichtbar übereinander liegen: die oberste und dahinter angedeutet zwei weitere. */
private const val VISIBLE_CARDS = 3

/** Ab diesem Anteil der Bildschirmbreite fliegt die Karte heraus (Entscheidung #3). */
private const val SWIPE_THRESHOLD = 0.3f

/** Maximale Neigung der Karte in Grad, wenn sie um eine Bildschirmbreite verschoben ist. */
private const val MAX_ROTATION = 12f

/** Höhe der Zeile „N übrig“ / „Überspringen“; die Snackbar sitzt darüber. */
val StackFooterHeight = 48.dp

/**
 * Kartenstapel für den Filter „Neu“ (Entscheidungen #3, #6): oben die erste Karte der geladenen
 * Liste, dahinter zwei weitere. Wischen rechts = interessant, links = uninteressant, ohne
 * Richtungshinweise; darunter „N übrig“ und „Überspringen“.
 *
 * Vertikal scrollbar, damit Pull-to-Refresh auch über dem Stapel greift.
 */
@Composable
fun Kartenstapel(
    items: List<Inserat>,
    moreAvailable: Boolean,
    onRate: (Inserat, Label) -> Unit,
    onSkip: (Inserat) -> Unit,
    onOpen: (Inserat) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.height(height).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 20.dp)) {
                    val visible = items.take(VISIBLE_CARDS)
                    // Von hinten nach vorn zeichnen; der Schlüssel hält Zustand und Animation je Karte.
                    visible.asReversed().forEach { inserat ->
                        key(inserat.id) {
                            StackItem(
                                inserat = inserat,
                                depth = visible.indexOf(inserat),
                                onRate = { label -> onRate(inserat, label) },
                                onClick = { onOpen(inserat) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(StackFooterHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatRemaining(items.size, moreAvailable),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { items.firstOrNull()?.let(onSkip) }) {
                        Text(stringResource(R.string.stack_skip), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Eine Karte im Stapel; nur die oberste ([depth] 0) lässt sich wischen und antippen. */
@Composable
private fun StackItem(inserat: Inserat, depth: Int, onRate: (Label) -> Unit, onClick: () -> Unit) {
    // Rückt eine Karte nach vorn, wächst sie weich auf volle Größe.
    val animatedDepth by animateFloatAsState(depth.toFloat(), label = "depth")
    val isTop = depth == 0
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenWidth = LocalWindowInfo.current.containerSize.width.toFloat().coerceAtLeast(1f)
    val threshold = screenWidth * SWIPE_THRESHOLD
    val flingVelocity = with(density) { 1_000.dp.toPx() }
    val stackOffset = with(density) { 10.dp.toPx() }
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val currentOnRate by rememberUpdatedState(onRate)

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            offset.snapTo(offset.value + delta)
            // Haptisches Signal beim Überschreiten der Schwelle (Entscheidung #3).
            val beyond = abs(offset.value) > threshold
            if (beyond != armed) {
                armed = beyond
                if (beyond) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Die Karten dahinter schauen unten etwas hervor.
                transformOrigin = TransformOrigin(0.5f, 1f)
                val scale = 1f - 0.05f * animatedDepth
                scaleX = scale
                scaleY = scale
                translationY = stackOffset * animatedDepth
                translationX = offset.value
                rotationZ = offset.value / screenWidth * MAX_ROTATION
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = isTop,
                onDragStopped = { velocity ->
                    val x = offset.value
                    val direction = when {
                        x > threshold || (velocity > flingVelocity && x > 0f) -> 1
                        x < -threshold || (velocity < -flingVelocity && x < 0f) -> -1
                        else -> 0
                    }
                    armed = false
                    if (direction == 0) {
                        offset.animateTo(0f, spring())
                    } else {
                        offset.animateTo(direction * screenWidth * 1.5f, tween(durationMillis = 200))
                        currentOnRate(if (direction > 0) Label.INTERESSANT else Label.UNINTERESSANT)
                    }
                },
            )
            .clickable(enabled = isTop, onClick = onClick),
    ) {
        StackCard(inserat, elevated = depth < VISIBLE_CARDS - 1)
    }
}

/**
 * Karte (Entscheidung #6): Bild mit Quelle oben links und Energieklasse oben rechts
 * (Haus/Wohnung), Preis groß, Titel bis zu zwei Zeilen, PLZ Ort, Eckdaten.
 */
@Composable
private fun StackCard(inserat: Inserat, elevated: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            InseratImage(inserat, Modifier.fillMaxSize(), iconSize = 72.dp)
            inserat.source?.takeIf { it.isNotBlank() }?.let { source ->
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (inserat.propertyType.hasEnergyClass) {
                EnergyClassBadge(inserat.energyEfficiencyClass, Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = formatPrice(inserat.price),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            inserat.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val place = formatPlace(inserat)
            if (place.isNotEmpty()) Text(place, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            val facts = formatFacts(inserat)
            if (facts.isNotEmpty()) {
                Text(
                    text = facts,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(heightDp = 640)
@Composable
private fun KartenstapelPreview() {
    ImmoFinderTheme {
        Surface {
            Kartenstapel(
                items = List(4) { i ->
                    Inserat(
                        id = "$i", propertyType = PropertyType.HOUSE,
                        title = "Einfamilienhaus mit Garten – ruhige Lage, viel Platz für die Familie",
                        imageUrl = null, price = 450_000.0, zipCode = "91054", city = "Erlangen",
                        street = null, houseNumber = null, rooms = 5.0, livingArea = 140.0, plotArea = 600.0,
                        provider = null, url = null, source = "Kleinanzeigen", createdAt = null,
                        label = Label.UNBEWERTET, constructionYear = 1978, energyEfficiencyClass = "B",
                    )
                },
                moreAvailable = true,
                onRate = { _, _ -> },
                onSkip = {},
                onOpen = {},
            )
        }
    }
}
