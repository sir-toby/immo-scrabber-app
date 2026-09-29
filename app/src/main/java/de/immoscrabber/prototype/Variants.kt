// PROTOTYPE – Wegwerf-Code. Drei strukturell verschiedene Varianten der Swipe-Bewertung, umschaltbar über die schwebende Leiste.
//   A  Kartenstapel   – eine Karte nach der anderen, rechts/links bewerten, hoch = später
//   B  Wischliste     – normale Liste, Zeilen wie in Gmail wischen, Snackbar mit "Rückgängig"
//   C  Vollbild-Feed  – vertikal blättern (ohne zu bewerten), horizontal wischen bewertet
//   D  A+B kombiniert – "Neu" als Stapel (nur links/rechts, kleiner Überspringen-Button), andere Filter als Wischliste
package de.immoscrabber.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class VariantCallbacks(
    val onRate: (Listing, Label) -> Unit,
    val onSkip: (Listing) -> Unit,
    val onOpen: (Listing) -> Unit,
)

// ---------------------------------------------------------------- A: Kartenstapel

@Composable
fun VariantA(items: List<Listing>, type: PType, cb: VariantCallbacks) {
    if (items.isEmpty()) return EmptyState(type)
    Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp)) {
        val stack = items.take(3).reversed()
        stack.forEachIndexed { i, l ->
            val depth = stack.size - 1 - i
            key(l.id) {
                if (depth == 0) {
                    SwipeableCard(l, cb.onRate, cb.onSkip, cb.onOpen, Modifier.fillMaxSize()) { StackCard(l, cb) }
                } else {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val s = 1f - 0.05f * depth
                        scaleX = s; scaleY = s; translationY = 22.dp.toPx() * depth
                    }) { StackCard(l, cb) }
                }
            }
        }
        Text(
            "${items.size} übrig · ← Nö   ↑ später   Favorit →",
            style = MaterialTheme.typography.labelMedium, color = Color.Gray,
            modifier = Modifier.align(Alignment.BottomCenter).padding(top = 4.dp).graphicsLayer { translationY = 28.dp.toPx() },
        )
    }
}

@Composable
private fun StackCard(l: Listing, cb: VariantCallbacks) {
    Card(
        Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(6.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        ListingImage(l, Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.padding(16.dp)) {
            Text(l.priceText(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(l.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(l.placeText(), style = MaterialTheme.typography.bodyMedium)
            Text(l.factsText(), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------- B: Wischliste

@Composable
fun VariantB(items: List<Listing>, type: PType, showLabel: Boolean, cb: VariantCallbacks) {
    if (items.isEmpty()) return EmptyState(type)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(items, key = { it.id }) { l ->
            val state = rememberSwipeToDismissBoxState()
            LaunchedEffect(state.currentValue) {
                when (state.currentValue) {
                    SwipeToDismissBoxValue.StartToEnd -> cb.onRate(l, Label.INTERESTING)
                    SwipeToDismissBoxValue.EndToStart -> cb.onRate(l, Label.UNINTERESTING)
                    SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
                }
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBox(
                state = state,
                modifier = Modifier.animateItem(),
                backgroundContent = {
                    val dir = state.dismissDirection
                    val (color, icon, text, align) = when (dir) {
                        SwipeToDismissBoxValue.StartToEnd -> Quad(Green, Icons.Filled.Favorite, "Favorit", Alignment.CenterStart)
                        SwipeToDismissBoxValue.EndToStart -> Quad(Red, Icons.Filled.ThumbDown, "Archiv", Alignment.CenterEnd)
                        else -> Quad(Color.Transparent, null, "", Alignment.Center)
                    }
                    Box(Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp), contentAlignment = align) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            icon?.let { Icon(it, null, tint = Color.White) }
                            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            ) { ListRow(l, showLabel, cb) }
            HorizontalDivider()
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun ListRow(l: Listing, showLabel: Boolean, cb: VariantCallbacks) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { cb.onOpen(l) }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListingImage(l, Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)), badges = false)
        Column(Modifier.weight(1f)) {
            Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(l.placeText(), style = MaterialTheme.typography.bodySmall)
            Text(l.factsText(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(l.priceText(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(l.source, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                l.energyClass?.let { EnergyBadge(it) }
            }
            if (showLabel && l.label != Label.OPEN) {
                Text(
                    l.label.title, style = MaterialTheme.typography.labelSmall,
                    color = if (l.label == Label.INTERESTING) Green else Red,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- C: Vollbild-Feed

@Composable
fun VariantC(items: List<Listing>, type: PType, filter: Filter, cb: VariantCallbacks) {
    if (items.isEmpty()) return EmptyState(type)
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    VerticalPager(pager, Modifier.fillMaxSize(), key = { items[it].id }) { page ->
        val l = items[page]
        SwipeableCard(
            l,
            onRate = { listing, label ->
                val stays = filter.label == null || filter.label == label
                cb.onRate(listing, label)
                // Bleibt die Karte im Filter sichtbar (z. B. "Alle"), selbst weiterblättern.
                if (stays) scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(items.size - 1)) }
            },
            onSkip = null,
            onTap = cb.onOpen,
            modifier = Modifier.fillMaxSize(),
        ) { FullscreenCard(l, page, items.size, cb) }
    }
}

@Composable
private fun FullscreenCard(l: Listing, page: Int, total: Int, cb: VariantCallbacks) {
    Box(Modifier.fillMaxSize()) {
        ListingImage(l, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 96.dp),
        ) {
            Text(l.priceText(), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(l.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("${l.placeText()} · ${l.factsText()}", color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${page + 1}/$total · hoch = nächstes · ← Nö / Favorit →",
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- D: A+B kombiniert

@Composable
fun VariantD(items: List<Listing>, type: PType, filter: Filter, cb: VariantCallbacks) {
    if (filter != Filter.NEU) return VariantB(items, type, showLabel = filter == Filter.ALLE, cb)
    if (items.isEmpty()) return EmptyState(type)
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val stack = items.take(3).reversed()
            stack.forEachIndexed { i, l ->
                val depth = stack.size - 1 - i
                key(l.id) {
                    if (depth == 0) {
                        SwipeableCard(l, cb.onRate, onSkip = null, onTap = cb.onOpen, modifier = Modifier.fillMaxSize()) { StackCard(l, cb) }
                    } else {
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val s = 1f - 0.05f * depth
                            scaleX = s; scaleY = s; translationY = 22.dp.toPx() * depth
                        }) { StackCard(l, cb) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "← Nö · Favorit →   ${items.size} übrig",
                style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.weight(1f),
            )
            val top = items.first()
            TextButton(onClick = { cb.onSkip(top) }) { Text("Überspringen", style = MaterialTheme.typography.labelMedium) }
        }
    }
}
