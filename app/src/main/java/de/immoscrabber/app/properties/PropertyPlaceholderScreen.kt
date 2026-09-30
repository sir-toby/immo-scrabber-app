package de.immoscrabber.app.properties

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.PropertyType

/** Vorläufiger Tab für Wohnungen und Grundstücke; deren Ticket ersetzt ihn durch [PropertyTab]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyPlaceholderScreen(type: PropertyType) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(type.pluralName)) }) }) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tab_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
