// PROTOTYPE – Wegwerf-Code. Gemeinsame Bausteine der Swipe-Varianten.
package de.immoscrabber.prototype

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)

fun PType.icon() = when (this) {
    PType.HOUSE -> Icons.Outlined.Home
    PType.FLAT -> Icons.Outlined.Apartment
    PType.SITE -> Icons.Outlined.Landscape
}

/** Platzhalter statt Anbieter-Bild (die echten Bilder werden gehotlinkt, siehe API-Recherche). */
@Composable
fun ListingImage(listing: Listing, modifier: Modifier = Modifier, badges: Boolean = true) {
    val hue = (listing.id * 47 % 360).toFloat()
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color.hsv(hue, 0.35f, 0.85f), Color.hsv((hue + 40) % 360, 0.5f, 0.55f)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(listing.type.icon(), null, Modifier.size(72.dp), tint = Color.White.copy(alpha = 0.6f))
        if (badges) {
            Text(
                listing.source, color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            listing.energyClass?.let { EnergyBadge(it, Modifier.align(Alignment.TopEnd).padding(8.dp)) }
        }
    }
}

@Composable
fun EnergyBadge(cls: String, modifier: Modifier = Modifier) {
    val colors = mapOf(
        "A+" to 0xFF00843D, "A" to 0xFF23A638, "B" to 0xFF8CC63F, "C" to 0xFFD7DF23, "D" to 0xFFFFF200,
        "E" to 0xFFFBB040, "F" to 0xFFF26522, "G" to 0xFFED1C24, "H" to 0xFFB71C1C,
    )
    Text(
        cls, fontWeight = FontWeight.Bold, color = if (cls in listOf("C", "D", "E")) Color.Black else Color.White,
        modifier = modifier.background(Color(colors[cls] ?: 0xFF888888), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun BoxScope.Stamp(text: String, color: Color, alpha: Float, align: Alignment, angle: Float) {
    if (alpha <= 0f) return
    Text(
        text, color = color, fontSize = 30.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.align(align).padding(28.dp).rotate(angle).graphicsLayer { this.alpha = alpha }
            .border(4.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/**
 * Karte, die man nach rechts (interessant) / links (uninteressant) und optional nach oben (überspringen) wischt.
 * Ab der Schwelle (30 % Bildschirmbreite) gibt es ein haptisches Signal; loslassen darunter federt zurück.
 */
@Composable
fun SwipeableCard(
    listing: Listing,
    onRate: (Listing, Label) -> Unit,
    onSkip: ((Listing) -> Unit)?,
    onTap: (Listing) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = widthPx * 0.3f
    val x = remember(listing.id) { Animatable(0f) }
    val y = remember(listing.id) { Animatable(0f) }
    var armed by remember(listing.id) { mutableStateOf(false) }
    val rate by rememberUpdatedState(onRate)
    val skip by rememberUpdatedState(onSkip)
    val tap by rememberUpdatedState(onTap)

    fun check() {
        val now = abs(x.value) > threshold || (skip != null && -y.value > threshold)
        if (now != armed) {
            armed = now
            if (now) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun release() {
        scope.launch {
            when {
                x.value > threshold -> { x.animateTo(widthPx * 1.5f, tween(180)); rate(listing, Label.INTERESTING) }
                x.value < -threshold -> { x.animateTo(-widthPx * 1.5f, tween(180)); rate(listing, Label.UNINTERESTING) }
                skip != null && -y.value > threshold -> { y.animateTo(-widthPx * 2.5f, tween(180)); skip?.invoke(listing) }
                else -> { launch { y.animateTo(0f, spring()) }; x.animateTo(0f, spring()) }
            }
            // Karte bleibt ggf. in der Liste (z. B. Filter "Alle") – dann wieder zentrieren.
            x.snapTo(0f); y.snapTo(0f); armed = false
        }
    }

    val gestures = if (onSkip != null) {
        Modifier.pointerInput(listing.id) {
            detectDragGestures(onDragEnd = ::release, onDragCancel = ::release) { change, d ->
                change.consume()
                scope.launch { x.snapTo(x.value + d.x); y.snapTo(y.value + d.y); check() }
            }
        }
    } else {
        Modifier.pointerInput(listing.id) {
            detectHorizontalDragGestures(onDragEnd = ::release, onDragCancel = ::release) { change, dx ->
                change.consume()
                scope.launch { x.snapTo(x.value + dx); check() }
            }
        }
    }

    Box(
        modifier
            .graphicsLayer {
                translationX = x.value; translationY = y.value
                rotationZ = x.value / widthPx * 12f
            }
            .pointerInput(listing.id) { detectTapGestures(onTap = { tap(listing) }) }
            .then(gestures),
    ) {
        content()
        val p = (x.value / threshold).coerceIn(-1f, 1f)
        Stamp("INTERESSANT", Green, p.coerceAtLeast(0f), Alignment.TopStart, -14f)
        Stamp("NÖ", Red, (-p).coerceAtLeast(0f), Alignment.TopEnd, 14f)
        if (onSkip != null) {
            Stamp("SPÄTER", Color.DarkGray, (-y.value / threshold).coerceIn(0f, 1f), Alignment.BottomCenter, 0f)
        }
    }
}

@Composable
fun EmptyState(type: PType, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Icon(type.icon(), null, Modifier.size(96.dp), tint = Color.Gray)
        Text("Momentan keine ${type.title} zu sehen", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
    }
}
