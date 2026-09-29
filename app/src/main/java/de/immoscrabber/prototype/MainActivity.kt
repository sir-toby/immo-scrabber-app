// PROTOTYPE – Wegwerf-Code für das Wayfinder-Ticket "Swipe-Bewertung: Interaktionsmodell".
// Drei Varianten der Swipe-Bewertung auf dem echten Screen-Gerüst (Bottom Navigation + Filter-Chips),
// umschaltbar über die schwarze Leiste unten (◀ ▶). Alle Daten in-memory; die Statuszeile zeigt,
// welcher API-Call die echte App absetzen würde.
package de.immoscrabber.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { App() } }
    }
}

private val variants = listOf("A" to "Kartenstapel", "B" to "Wischliste", "C" to "Vollbild-Feed")

@Composable
fun App() {
    val store = remember { Store() }
    var tab by rememberSaveable { mutableIntStateOf(1) } // 0 = Einstellungen, 1..3 = Typen
    var variant by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 56.dp)) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("Einstellungen") })
                PType.entries.forEachIndexed { i, t ->
                    NavigationBarItem(tab == i + 1, { tab = i + 1 }, { Icon(t.icon(), null) }, label = { Text(t.title) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) {
                SettingsPlaceholder()
            } else {
                ListingScreen(store, PType.entries[tab - 1], variant, snackbar)
            }
            VariantSwitcher(
                variant, { variant = (it + variants.size) % variants.size },
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun ListingScreen(store: Store, type: PType, variant: Int, snackbar: SnackbarHostState) {
    var filter by rememberSaveable(type) { mutableStateOf(Filter.NEU) }
    val scope = rememberCoroutineScope()
    val items = store.visible(type, filter)

    val cb = VariantCallbacks(
        onRate = { l, label ->
            store.rate(l, label, moveToEnd = variant == 0)
            if (variant == 1) scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(
                    if (label == Label.INTERESTING) "Als Favorit markiert" else "Ins Archiv verschoben",
                    actionLabel = "Rückgängig", duration = SnackbarDuration.Short,
                )
                if (r == SnackbarResult.ActionPerformed) store.undo()
            }
        },
        onSkip = { store.skip(it) },
        onOpen = { l ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("Würde Inserat im Browser öffnen: ${l.source} – ${l.url}", duration = SnackbarDuration.Short)
            }
        },
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(type.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = store::undo, enabled = store.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Rückgängig")
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Filter.entries.forEach { f ->
                val n = f.label?.let { store.count(type, it) } ?: store.listings.count { it.type == type }
                FilterChip(filter == f, { filter = f }, { Text("${f.title} ($n)") })
            }
        }
        // Zustand sichtbar machen: letzter (simulierter) API-Call
        Text(
            store.lastAction, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 2,
        )
        Box(Modifier.weight(1f)) {
            when (variant) {
                0 -> VariantA(items, type, cb)
                1 -> VariantB(items, type, showLabel = filter == Filter.ALLE, cb)
                else -> VariantC(items, type, filter, cb)
            }
        }
    }
}

@Composable
private fun VariantSwitcher(current: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = Color(0xEE111111), shadowElevation = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onChange(current - 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorherige Variante", tint = Color.White) }
            val (k, name) = variants[current]
            Text("$k · $name", color = Color.White, style = MaterialTheme.typography.labelLarge)
            IconButton({ onChange(current + 1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächste Variante", tint = Color.White) }
        }
    }
}

@Composable
private fun SettingsPlaceholder() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        Text("Platzhalter – Suchprofile wie im Web, Logout, Server-URL, später Push.")
        Text("Wird im Ticket \"Einstellungen: Suchprofile mobil\" entschieden.", color = Color.Gray)
    }
}
