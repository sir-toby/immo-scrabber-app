package de.immoscrabber.app.properties

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
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
 *
 * Long Press auf eine bewertete Zeile öffnet ein Menü mit „Zurück zu Neu“ (#14); für TalkBack steht
 * die Aktion zusätzlich direkt als Custom Action an der Zeile. Unbewertete Zeilen (unter „Alle“)
 * haben kein Menü. Long Press und Wischen kommen sich nicht in die Quere: Sobald der Finger sich
 * über die Touch-Slop hinaus bewegt, übernimmt das Wischen und der Long Press entfällt.
 */
@Composable
fun SwipeableInseratRow(
    inserat: Inserat,
    filter: Filter,
    onBewerten: (Label) -> Unit,
    onZurueckZuNeu: () -> Unit,
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

    val bewertet = inserat.label != Label.UNBEWERTET
    var menuOpen by remember { mutableStateOf(false) }
    val backToNew = stringResource(R.string.back_to_new)
    val a11y = if (bewertet) {
        Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(backToNew) { onZurueckZuNeu(); true })
        }
    } else {
        Modifier
    }

    Box(modifier) {
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = filter != Filter.Favoriten,
            enableDismissFromEndToStart = filter != Filter.Archiv,
            backgroundContent = { SwipeBackground(state.dismissDirection) },
        ) {
            InseratRow(
                inserat,
                showLabel = filter == Filter.Alle,
                onClick = onClick,
                // An der Zeile selbst: TalkBack fokussiert den zusammengeführten klickbaren Knoten.
                modifier = a11y,
                // Unbewertet tut Long Press nichts (sonst zählte das Loslassen als Tipp und öffnete das Inserat).
                onLongClick = if (bewertet) ({ menuOpen = true }) else ({}),
                onLongClickLabel = if (bewertet) stringResource(R.string.row_menu) else null,
            )
        }
        // Unter der Zeile, auf Höhe ihres Innenabstands.
        DropdownMenu(
            expanded = menuOpen && bewertet,
            onDismissRequest = { menuOpen = false },
            offset = DpOffset(16.dp, 0.dp),
        ) {
            DropdownMenuItem(
                text = { Text(backToNew) },
                onClick = {
                    menuOpen = false
                    onZurueckZuNeu()
                },
            )
        }
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
