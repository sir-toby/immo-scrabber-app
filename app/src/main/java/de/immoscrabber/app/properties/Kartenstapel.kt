package de.immoscrabber.app.properties

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.sp
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme
import de.immoscrabber.app.core.ui.theme.immoColors
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Karten, die sichtbar übereinander liegen: die oberste und dahinter angedeutet zwei weitere. */
private const val VISIBLE_CARDS = 3

/** Ab diesem Anteil der Bildschirmbreite fliegt die Karte heraus (Entscheidung #3). */
private const val SWIPE_THRESHOLD = 0.3f

/** Maximale Neigung der Karte in Grad, wenn sie um eine Bildschirmbreite verschoben ist. */
private const val MAX_ROTATION = 12f

/** Ab dieser Geschwindigkeit fliegt die Karte auch vor der Schwelle heraus. */
private val FLING_VELOCITY = 1_000.dp

/** Neigung des Stempels in Grad. */
private const val STAMP_ROTATION = 15f

/** Höhe der Zeile „N übrig“ / „Überspringen“; die Snackbar sitzt darüber. */
val StackFooterHeight = 48.dp

/**
 * Kartenstapel für den Filter „Neu“ (Entscheidungen #3, #6): oben die erste Karte der geladenen
 * Liste, dahinter zwei weitere. Wischen rechts = interessant, links = uninteressant; beim Ziehen
 * zeigt ein Stempel die Richtung, sonst keine Richtungshinweise. Darunter „N übrig“ und
 * „Überspringen“.
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
                    for (depth in visible.indices.reversed()) {
                        val inserat = visible[depth]
                        key(inserat.id) {
                            StackItem(
                                inserat = inserat,
                                depth = depth,
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

/**
 * Zustand des Wischens einer Karte. [offset] ändert sich synchron mit jedem Zug-Delta; nur das
 * Einrasten läuft als Animation. So kann kein verspätetes Delta eine laufende Animation
 * abbrechen und die Karte mitten im Bild stehen lassen.
 */
private class SwipeState {
    var offset by mutableFloatStateOf(0f)

    /** Die Karte fliegt heraus; die Bewertung ist entschieden, weitere Gesten zählen nicht. */
    var flyingOut by mutableStateOf(false)

    /** Haptisches Signal beim Überschreiten der Schwelle, einmal je Überschreiten. */
    var armed = false
    var settle: Job? = null
}

/** Eine Karte im Stapel; nur die oberste ([depth] 0) lässt sich wischen und antippen. */
@Composable
private fun StackItem(inserat: Inserat, depth: Int, onRate: (Label) -> Unit, onClick: () -> Unit) {
    // Rückt eine Karte nach vorn, wächst sie weich auf volle Größe.
    val animatedDepth by animateFloatAsState(depth.toFloat(), label = "depth")
    val isTop = depth == 0
    // Scope der Komposition, nicht des Gesten-Modifiers: Das Einrasten läuft weiter, auch wenn
    // der Modifier neu aufgesetzt wird, und endet erst, wenn die Karte den Stapel verlässt.
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenWidth = LocalWindowInfo.current.containerSize.width.toFloat().coerceAtLeast(1f)
    val threshold = screenWidth * SWIPE_THRESHOLD
    val flingVelocity = with(density) { FLING_VELOCITY.toPx() }
    val stackOffset = with(density) { 10.dp.toPx() }
    val swipe = remember { SwipeState() }
    val currentOnRate by rememberUpdatedState(onRate)
    val currentOnClick by rememberUpdatedState(onClick)
    // Gesten sind auf jeder Karte aktiv und prüfen erst beim Auslösen, ob sie oben liegt: Schaltete
    // `enabled` mitten in einer Geste um (die Karte davor fliegt gerade heraus), setzt Compose die
    // Zeigerverarbeitung zurück, und ein Wisch käme als Tippen an.
    val currentIsTop by rememberUpdatedState(isTop)

    // Nach jedem Loslassen oder Abbruch: herausfliegen und bewerten oder zurück in die Mitte.
    fun settle(velocity: Float) {
        if (swipe.flyingOut) return
        swipe.settle?.cancel()
        swipe.armed = false
        val x = swipe.offset
        val direction = when {
            x > threshold || (velocity > flingVelocity && x > 0f) -> 1
            x < -threshold || (velocity < -flingVelocity && x < 0f) -> -1
            else -> 0
        }
        if (direction == 0) {
            swipe.settle = scope.launch {
                animate(x, 0f, velocity, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    swipe.offset = value
                }
            }
            return
        }
        swipe.flyingOut = true
        val target = direction * screenWidth * 1.5f
        // Mindestens mit Fling-Geschwindigkeit weiter; ein schneller Wisch fliegt schneller ab.
        val speed = max(abs(velocity), flingVelocity)
        val duration = (abs(target - x) / speed * 1000).toInt().coerceIn(120, 300)
        swipe.settle = scope.launch {
            try {
                animate(x, target, velocity, tween(duration, easing = LinearEasing)) { value, _ -> swipe.offset = value }
            } finally {
                // Auch wenn die Karte vorher den Stapel verlässt: Die Bewertung ist entschieden.
                currentOnRate(if (direction > 0) Label.INTERESSANT else Label.UNINTERESSANT)
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (swipe.flyingOut || !currentIsTop) return@rememberDraggableState
        swipe.offset += delta
        // Haptisches Signal beim Überschreiten der Schwelle (Entscheidung #3).
        val beyond = abs(swipe.offset) > threshold
        if (beyond != swipe.armed) {
            swipe.armed = beyond
            if (beyond) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Rutscht die Karte nach hinten, während sie verschoben ist (etwa weil eine zurückgeholte
    // Karte oben landet), endet die Geste womöglich ohne Loslassen: dann zurück in die Mitte.
    LaunchedEffect(isTop) {
        if (!isTop && !swipe.flyingOut && swipe.offset != 0f) settle(velocity = 0f)
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
                translationX = swipe.offset
                rotationZ = swipe.offset / screenWidth * MAX_ROTATION
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                // Ein neuer Griff fängt eine zurückfedernde Karte an ihrer Stelle auf.
                onDragStarted = { if (currentIsTop && !swipe.flyingOut) swipe.settle?.cancel() },
                // Kommt auch bei abgebrochener Geste (dann mit Geschwindigkeit 0).
                onDragStopped = { velocity -> if (currentIsTop || swipe.offset != 0f) settle(velocity) },
            )
            .clickable { if (currentIsTop && !swipe.flyingOut) currentOnClick() },
    ) {
        StackCard(inserat, elevated = depth < VISIBLE_CARDS - 1)
        SwipeStamp(offset = { swipe.offset }, threshold = threshold)
    }
}

/**
 * Stempel beim Ziehen (Entscheidung #3): rechts „INTERESSANT“ in Grün, links „VERWERFEN“ in Rot,
 * gedreht und umrandet; die Deckkraft wächst mit der Zugstrecke bis zur Schwelle.
 */
@Composable
private fun BoxScope.SwipeStamp(offset: () -> Float, threshold: Float) {
    val x = offset()
    if (x == 0f) return
    val interested = x > 0f
    val color = if (interested) MaterialTheme.immoColors.interessant else MaterialTheme.immoColors.uninteressant
    Text(
        text = stringResource(if (interested) R.string.stack_stamp_interested else R.string.stack_stamp_discard),
        color = color,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        maxLines = 1,
        modifier = Modifier
            // Der Stempel steht auf der Seite, von der die Karte wegzieht.
            .align(if (interested) Alignment.TopStart else Alignment.TopEnd)
            .padding(horizontal = 24.dp, vertical = 64.dp)
            .graphicsLayer {
                alpha = (abs(offset()) / threshold).coerceIn(0f, 1f)
                rotationZ = if (interested) -STAMP_ROTATION else STAMP_ROTATION
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), StampShape)
            .border(4.dp, color, StampShape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

private val StampShape = RoundedCornerShape(10.dp)

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
