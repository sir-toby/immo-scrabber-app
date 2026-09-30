package de.immoscrabber.app.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label

/**
 * Zeile der Wischliste mit Gmail-artigem Wischen (Entscheidung #6):
 * rechts = interessant (grün), links = uninteressant (rot).
 *
 * - Favoriten nur links, Archiv nur rechts: die Zeile verlässt die Liste.
 * - Alle beide Richtungen: die Zeile federt zurück, nur das Badge wechselt. Ein Wisch in
 *   Richtung des aktuellen Labels federt ohne Bewertung zurück.
 */
@Composable
fun SwipeableInseratRow(
    inserat: Inserat,
    filter: Filter,
    onBewerten: (Label) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLabel by rememberUpdatedState(inserat.label)
    val currentOnBewerten by rememberUpdatedState(onBewerten)
    val state = rememberSwipeToDismissBoxState(
        // Ohne Seiteneffekt: kann mehrfach gerufen werden. `false` federt zurück.
        confirmValueChange = { value -> value.label?.let { it != currentLabel } ?: true },
    )

    LaunchedEffect(state.currentValue) {
        val label = state.currentValue.label ?: return@LaunchedEffect
        currentOnBewerten(label)
        if (filter == Filter.Alle) {
            // Die Zeile bleibt stehen: sichtbar zurückfedern.
            state.reset()
        } else {
            // Die Zeile ist schon aus der Liste; kommt sie zurück (Fehler, Rückgängig), steht sie wieder.
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = filter != Filter.Favoriten,
        enableDismissFromEndToStart = filter != Filter.Archiv,
        backgroundContent = { SwipeBackground(state.dismissDirection) },
    ) {
        InseratRow(inserat, showLabel = filter == Filter.Alle, onClick = onClick)
    }
}

private val SwipeToDismissBoxValue.label: Label?
    get() = when (this) {
        SwipeToDismissBoxValue.StartToEnd -> swipeLabel(rightward = true)
        SwipeToDismissBoxValue.EndToStart -> swipeLabel(rightward = false)
        SwipeToDismissBoxValue.Settled -> null
    }

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val label = direction.label ?: return
    val icon = label.icon ?: return
    val rightward = direction == SwipeToDismissBoxValue.StartToEnd
    val content = label.onColor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(label.color)
            .padding(horizontal = 24.dp),
        contentAlignment = if (rightward) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rightward) {
                Icon(icon, contentDescription = null, tint = content)
                Text(stringResource(R.string.swipe_favorite), color = content, fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.swipe_archive), color = content, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = content)
            }
        }
    }
}
