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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import de.immoscrabber.app.core.ui.theme.ImmoFinderTheme
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
 * Fester Platz unter der Karte für die Snackbar (einzeilig 48 dp plus 12 dp Rand oben und unten),
 * damit sie Titel, Ort und Eckdaten nie verdeckt (#52). Er bleibt immer frei, so ändert die Karte
 * ihre Größe nicht, wenn eine Snackbar kommt oder geht.
 */
private val SnackbarSpace = 72.dp

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
    onBewerten: (Inserat, Label) -> Unit,
    onSkip: (Inserat) -> Unit,
    onOpen: (Inserat) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Karten, deren Bewertung entschieden ist und die gerade hinausfliegen. Sie liegen vorn in
    // [items], bis `onBewerten` sie entfernt; die Karte dahinter ist schon die oberste (#51).
    val flying = remember { mutableStateSetOf<String>() }
    // Bewertet wird in Wischreihenfolge, auch wenn eine spätere Karte schneller landet.
    val folge = remember { BewertungsFolge<Inserat> { it.id } }
    val currentOnBewerten by rememberUpdatedState(onBewerten)
    // Verlässt eine fliegende Karte den Stapel (Refresh), darf sie die Bewertungen dahinter nicht
    // aufhalten.
    LaunchedEffect(items) {
        val ids = items.mapTo(HashSet<Any>()) { it.id }
        for ((item, label) in folge.retain(ids)) {
            flying -= item.id
            currentOnBewerten(item, label)
        }
        flying.retainAll(ids)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.height(height).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = SnackbarSpace)) {
                    val visible = items.take(VISIBLE_CARDS + flying.size)
                    val flyingAhead = visible.runningFold(0) { n, item -> if (item.id in flying) n + 1 else n }
                    // Von hinten nach vorn zeichnen; der Schlüssel hält Zustand und Animation je Karte.
                    for (index in visible.indices.reversed()) {
                        val inserat = visible[index]
                        val depth = if (inserat.id in flying) 0 else index - flyingAhead[index]
                        key(inserat.id) {
                            StackItem(
                                inserat = inserat,
                                depth = depth,
                                onBewerten = { label ->
                                    for ((item, itemLabel) in folge.finish(inserat, label)) {
                                        flying -= item.id
                                        onBewerten(item, itemLabel)
                                    }
                                },
                                onFlyOut = {
                                    flying += inserat.id
                                    folge.start(inserat)
                                },
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
                    TextButton(onClick = { items.firstOrNull { it.id !in flying }?.let(onSkip) }) {
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

    /** `draggable` hat die laufende Geste als Ziehen erkannt. */
    var dragging = false

    /** Haptisches Signal beim Überschreiten der Schwelle, einmal je Überschreiten. */
    var armed = false
    var settle: Job? = null
}

/**
 * Eine Karte im Stapel; nur die oberste ([depth] 0) lässt sich wischen und antippen. [onFlyOut]
 * meldet, dass die Bewertung entschieden ist und die Karte hinausfliegt, [onBewerten] kommt danach.
 */
@Composable
private fun StackItem(
    inserat: Inserat,
    depth: Int,
    onBewerten: (Label) -> Unit,
    onFlyOut: () -> Unit,
    onClick: () -> Unit,
) {
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
    val currentOnBewerten by rememberUpdatedState(onBewerten)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFlyOut by rememberUpdatedState(onFlyOut)
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
        currentOnFlyOut()
        val target = direction * screenWidth * 1.5f
        // Mindestens mit Fling-Geschwindigkeit weiter; ein schneller Wisch fliegt schneller ab.
        val speed = max(abs(velocity), flingVelocity)
        val duration = (abs(target - x) / speed * 1000).toInt().coerceIn(120, 300)
        swipe.settle = scope.launch {
            try {
                animate(x, target, velocity, tween(duration, easing = LinearEasing)) { value, _ -> swipe.offset = value }
            } finally {
                // Auch wenn die Karte vorher den Stapel verlässt: Die Bewertung ist entschieden.
                currentOnBewerten(swipeLabel(rightward = direction > 0))
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

    // Unter Last kommen Bewegungen verspätet oder gar nicht an (#51); das Loslassen bringt dann
    // die restliche Strecke mit.
    val onRelease: (KartenGeste.Ende) -> Unit = { ende ->
        if (isTop && !swipe.flyingOut) {
            when (ende) {
                // Nur Aufsetzen und Loslassen: einrasten, als wäre die Karte um die ganze Strecke
                // gezogen worden. Herausfliegen oder zurückfedern, nie öffnen.
                is KartenGeste.Ende.Sprung -> {
                    swipe.settle?.cancel()
                    swipe.offset += ende.dx
                    settle(ende.velocity)
                }
                // Die Reststrecke zählt mit; `onDragStopped` rastet danach ein.
                is KartenGeste.Ende.Gezogen -> if (ende.restDx != 0f) {
                    swipe.offset += ende.restDx
                    if (!swipe.dragging) settle(velocity = 0f)
                }
                KartenGeste.Ende.Tippen, KartenGeste.Ende.Verworfen, KartenGeste.Ende.Gescrollt -> Unit
            }
        }
    }
    val currentOnRelease by rememberUpdatedState(onRelease)

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
            // Die hinausfliegende Karte nimmt keine Berührungen mehr an; sie gehen an die Karte darunter.
            .then(
                if (swipe.flyingOut) {
                    Modifier
                } else {
                    Modifier
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Horizontal,
                            // Ein neuer Griff fängt eine zurückfedernde Karte an ihrer Stelle auf.
                            onDragStarted = {
                                swipe.dragging = true
                                // Kommt verspätet über einen Kanal; rastet die Karte schon ein
                                // (das Loslassen war schneller), bleibt das Einrasten stehen.
                                if (currentIsTop && !swipe.flyingOut) swipe.settle?.cancel()
                            },
                            // Kommt auch bei abgebrochener Geste (dann mit Geschwindigkeit 0).
                            onDragStopped = { velocity ->
                                swipe.dragging = false
                                if (currentIsTop || swipe.offset != 0f) settle(velocity)
                            },
                        )
                        // Vor `clickable`: ein Loslassen weit weg vom Aufsetzpunkt ist nie ein Tippen (#51).
                        .pointerInput(Unit) { observeRelease { ende -> currentOnRelease(ende) } }
                        .clickable { if (currentIsTop) currentOnClick() }
                },
            ),
    ) {
        StackCard(inserat, elevated = depth < VISIBLE_CARDS - 1)
        SwipeStamp(offset = { swipe.offset }, threshold = threshold)
    }
}

/**
 * Beobachtet jede Berührung im Initial-Durchlauf, also vor `clickable` weiter innen, und meldet
 * beim Loslassen, was sie war ([KartenGeste]). [KartenGeste.Ende.Sprung] und
 * [KartenGeste.Ende.Verworfen] verbraucht sie: `clickable` bricht ab, statt das Inserat zu öffnen. Sonst verbraucht sie nichts, Tippen und
 * Ziehen behalten ihr Verhalten.
 */
private suspend fun PointerInputScope.observeRelease(onRelease: (KartenGeste.Ende) -> Unit) {
    val geste = KartenGeste(viewConfiguration.touchSlop)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        geste.down(down.position, down.uptimeMillis)
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) {
                val ende = geste.up(change.position, change.uptimeMillis, step = change.positionChangeIgnoreConsumed())
                if (ende is KartenGeste.Ende.Sprung || ende is KartenGeste.Ende.Verworfen) change.consume()
                onRelease(ende)
                break
            }
            geste.move(change.position)
        }
    }
}

/**
 * Stempel beim Ziehen (Entscheidung #3): rechts „INTERESSANT“ in Grün, links „VERWERFEN“ in Rot,
 * gedreht und umrandet; die Deckkraft wächst mit der Zugstrecke bis zur Schwelle.
 */
@Composable
private fun BoxScope.SwipeStamp(offset: () -> Float, threshold: Float) {
    // Die Zugstrecke wird nur im graphicsLayer gelesen; die Komposition hängt allein an der
    // Richtung und läuft nur neu, wenn diese kippt.
    val currentOffset by rememberUpdatedState(offset)
    val interested by remember { derivedStateOf { currentOffset() > 0f } }
    val color = swipeLabel(rightward = interested).color
    Text(
        text = stringResource(if (interested) R.string.stack_stamp_interested else R.string.stack_stamp_discard),
        color = color,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        maxLines = 1,
        modifier = Modifier
            // Dekoration: Die Bewertung sagen Snackbar und Label an, nicht der Stempel.
            .clearAndSetSemantics {}
            // Der Stempel steht auf der Seite, von der die Karte wegzieht.
            .align(if (interested) Alignment.TopStart else Alignment.TopEnd)
            .padding(horizontal = 24.dp, vertical = 64.dp)
            .graphicsLayer {
                alpha = (abs(currentOffset()) / threshold).coerceIn(0f, 1f)
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
                items = List(4) { i -> previewInserat(id = "$i", label = Label.UNBEWERTET) },
                moreAvailable = true,
                onBewerten = { _, _ -> },
                onSkip = {},
                onOpen = {},
            )
        }
    }
}
