package de.immoscrabber.app.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.immoscrabber.app.R
import de.immoscrabber.app.core.data.SuchprofilRepository
import de.immoscrabber.app.core.model.PropertyType
import de.immoscrabber.app.core.ui.icon

/**
 * Einstieg der Editor-Route: ViewModel am Back-Stack-Eintrag des Editors.
 *
 * @param onClose Zurück ohne Speichern (nach „Änderungen verwerfen?“).
 * @param onFertig gespeichert oder gelöscht; schließt den Editor und zeigt [String] als Snackbar.
 */
@Composable
fun SuchprofilEditor(
    repository: SuchprofilRepository,
    profilId: String?,
    vorauswahl: PropertyType?,
    onClose: () -> Unit,
    onFertig: (String) -> Unit,
) {
    val viewModel: SuchprofilEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { SuchprofilEditorViewModel(repository, profilId, vorauswahl) } },
    )
    SuchprofilEditorScreen(viewModel, onClose, onFertig)
}

/**
 * Vollbild-Editor (Entscheidung #8): Zurück + „Speichern“ in der App-Bar, Löschen im ⋮-Menü mit
 * Bestätigung, „Änderungen verwerfen?“ beim Verlassen mit Änderungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuchprofilEditorScreen(
    viewModel: SuchprofilEditorViewModel,
    onClose: () -> Unit,
    onFertig: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val texts = EditorTexts(
        saved = stringResource(R.string.editor_saved),
        savedNewPlace = stringResource(R.string.editor_saved_new_place),
        deleted = stringResource(R.string.editor_deleted),
        place = stringResource(R.string.editor_failed_place),
        unreachable = stringResource(R.string.editor_failed_unreachable),
        failedSave = stringResource(R.string.editor_failed_save),
        failedSaveHttp = stringResource(R.string.editor_failed_save_http),
        failedDelete = stringResource(R.string.editor_failed_delete),
        failedDeleteHttp = stringResource(R.string.editor_failed_delete_http),
    )
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Gespeichert -> onFertig(if (event.neuerOrt) texts.savedNewPlace else texts.saved)
                EditorEvent.Geloescht -> onFertig(texts.deleted)
                is EditorEvent.Fehler -> snackbarHostState.showSnackbar(
                    texts.fehlerText(event.fehler, beimLoeschen = event.aktion == Aktion.Loeschen),
                )
            }
        }
    }
    // Profil nicht mehr im Speicher (etwa nach dem Beenden durch das System): zurück zur Liste.
    LaunchedEffect(state.nichtGefunden) { if (state.nichtGefunden) onClose() }

    val zurueck = {
        when {
            state.gesperrt -> Unit
            state.geaendert -> confirmDiscard = true
            else -> onClose()
        }
    }
    BackHandler(enabled = state.gesperrt || state.geaendert) { zurueck() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.neu) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = zurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                    }
                },
                actions = {
                    if (state.aktion == Aktion.Speichern) {
                        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        TextButton(onClick = viewModel::speichern, enabled = !state.gesperrt) {
                            Text(stringResource(R.string.editor_save))
                        }
                    }
                    if (!state.neu) {
                        LoeschenMenue(enabled = !state.gesperrt, onDelete = { confirmDelete = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Formular(state, viewModel::aendern)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.editor_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.editor_discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.loeschen()
                }) { Text(stringResource(R.string.editor_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LoeschenMenue(enabled: Boolean, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun Formular(state: EditorUiState, aendern: ((SuchprofilFormular) -> SuchprofilFormular) -> Unit) {
    val form = state.form
    val enabled = !state.gesperrt
    val fehler = state.fehler

    if (state.neu) {
        TypAuswahl(form.type, enabled, fehler[Feld.Typ]) { type -> aendern { it.copy(type = type) } }
    } else {
        form.type?.let { type ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(type.editorName), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    val type = form.type ?: return
    val felder = form.felder

    Abschnitt(R.string.editor_section_place)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Feldtext(
            value = form.zipCode,
            onChange = { v -> aendern { it.copy(zipCode = v) } },
            label = R.string.editor_zip,
            fehler = fehler[Feld.Plz],
            enabled = enabled,
            nurZiffern = 5,
            modifier = Modifier.weight(0.4f),
        )
        Feldtext(
            value = form.city,
            onChange = { v -> aendern { it.copy(city = v) } },
            label = R.string.editor_city,
            fehler = fehler[Feld.Stadt],
            enabled = enabled,
            modifier = Modifier.weight(0.6f),
        )
    }
    Feldtext(
        value = form.radius,
        onChange = { v -> aendern { it.copy(radius = v) } },
        label = R.string.editor_radius,
        fehler = fehler[Feld.Radius],
        enabled = enabled,
        nurZiffern = 4,
        einheit = R.string.editor_unit_km,
    )

    Abschnitt(R.string.editor_section_limits)
    Text(
        stringResource(R.string.editor_limits_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (Feld.Preis in felder) {
        Feldtext(
            value = form.priceLimit,
            onChange = { v -> aendern { it.copy(priceLimit = v) } },
            label = R.string.editor_price,
            fehler = fehler[Feld.Preis],
            enabled = enabled,
            nurZiffern = 9,
            einheit = R.string.editor_unit_euro,
            visualTransformation = TausenderTrenner,
        )
    }
    if (Feld.Zimmer in felder) {
        Feldtext(
            value = form.minRooms,
            onChange = { v -> aendern { it.copy(minRooms = v) } },
            label = R.string.editor_rooms,
            fehler = fehler[Feld.Zimmer],
            enabled = enabled,
            nurZiffern = 2,
        )
    }
    if (Feld.BaujahrBis in felder) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Feldtext(
                value = form.minConstructionYear,
                onChange = { v -> aendern { it.copy(minConstructionYear = v) } },
                label = R.string.editor_year_from_house,
                fehler = fehler[Feld.BaujahrVon],
                enabled = enabled,
                nurZiffern = 4,
                modifier = Modifier.weight(1f),
            )
            Feldtext(
                value = form.maxConstructionYear,
                onChange = { v -> aendern { it.copy(maxConstructionYear = v) } },
                label = R.string.editor_year_to_house,
                fehler = fehler[Feld.BaujahrBis],
                enabled = enabled,
                nurZiffern = 4,
                modifier = Modifier.weight(1f),
            )
        }
    } else if (Feld.BaujahrVon in felder) {
        Feldtext(
            value = form.minConstructionYear,
            onChange = { v -> aendern { it.copy(minConstructionYear = v) } },
            label = R.string.editor_year_from_flat,
            fehler = fehler[Feld.BaujahrVon],
            enabled = enabled,
            nurZiffern = 4,
        )
    }
    if (Feld.Flaeche in felder) {
        Feldtext(
            value = form.minArea,
            onChange = { v -> aendern { it.copy(minArea = v) } },
            label = if (type == PropertyType.HOUSE) R.string.editor_area_house else R.string.editor_area_site,
            fehler = fehler[Feld.Flaeche],
            enabled = enabled,
            nurZiffern = 7,
            einheit = R.string.editor_unit_sqm,
            visualTransformation = TausenderTrenner,
        )
    }
    if (Feld.Anbieter in felder) {
        Abschnitt(R.string.editor_providers)
        AnbieterChips(form, enabled, aendern)
    }
}

@Composable
private fun TypAuswahl(selected: PropertyType?, enabled: Boolean, fehler: Feldfehler?, onSelect: (PropertyType) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        PropertyType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, PropertyType.entries.size),
                icon = { SegmentedButtonDefaults.Icon(active = type == selected) { Icon(type.icon, null, Modifier.size(18.dp)) } },
                label = { Text(stringResource(type.editorName), maxLines = 1) },
            )
        }
    }
    Text(
        text = stringResource(if (fehler != null) R.string.editor_error_type else R.string.editor_type_hint),
        style = MaterialTheme.typography.bodySmall,
        color = if (fehler != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnbieterChips(
    form: SuchprofilFormular,
    enabled: Boolean,
    aendern: ((SuchprofilFormular) -> SuchprofilFormular) -> Unit,
) {
    val hinzufuegen = { aendern { it.anbieterHinzufuegen() } }
    OutlinedTextField(
        value = form.anbieterEingabe,
        onValueChange = { v -> aendern { it.copy(anbieterEingabe = v) } },
        label = { Text(stringResource(R.string.editor_providers_input)) },
        supportingText = { Text(stringResource(R.string.editor_providers_hint)) },
        trailingIcon = {
            IconButton(onClick = hinzufuegen, enabled = enabled && form.anbieterEingabe.isNotBlank()) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.editor_providers_add))
            }
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { hinzufuegen() }),
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        form.ausgeschlosseneAnbieter.forEach { anbieter ->
            InputChip(
                selected = false,
                enabled = enabled,
                onClick = { aendern { f -> f.copy(ausgeschlosseneAnbieter = f.ausgeschlosseneAnbieter - anbieter) } },
                label = { Text(anbieter) },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.editor_providers_remove, anbieter),
                        Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun Abschnitt(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/**
 * Textfeld des Editors. Mit [nurZiffern] (Höchstlänge) gibt es die Zifferntastatur und nur Ziffern
 * werden angenommen.
 */
@Composable
private fun Feldtext(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    fehler: Feldfehler?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    nurZiffern: Int? = null,
    @StringRes einheit: Int? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { neu ->
            if (nurZiffern == null) {
                onChange(neu)
            } else {
                onChange(neu.filter(Char::isDigit).take(nurZiffern))
            }
        },
        label = { Text(stringResource(label), maxLines = 1) },
        suffix = einheit?.let { { Text(stringResource(it)) } },
        isError = fehler != null,
        supportingText = fehler?.let { { Text(stringResource(it.text)) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (nurZiffern != null) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (nurZiffern == null) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
        ),
        // In einer Row begrenzt das weight des Aufrufers die Breite.
        modifier = modifier.fillMaxWidth(),
    )
}

@get:StringRes
private val Feldfehler.text: Int
    get() = when (this) {
        Feldfehler.TypFehlt -> R.string.editor_error_type
        Feldfehler.PlzUngueltig -> R.string.editor_error_zip
        Feldfehler.StadtFehlt -> R.string.editor_error_city
        Feldfehler.RadiusUngueltig -> R.string.editor_error_radius
        Feldfehler.KeineGanzeZahl -> R.string.editor_error_number
        Feldfehler.BaujahrUngueltig -> R.string.editor_error_year
        Feldfehler.BaujahrReihenfolge -> R.string.editor_error_year_order
    }

@get:StringRes
private val PropertyType.editorName: Int
    get() = when (this) {
        PropertyType.HOUSE -> R.string.editor_type_house
        PropertyType.FLAT -> R.string.editor_type_flat
        PropertyType.SITE -> R.string.editor_type_site
    }

private class EditorTexts(
    val saved: String,
    val savedNewPlace: String,
    val deleted: String,
    val place: String,
    val unreachable: String,
    val failedSave: String,
    val failedSaveHttp: String,
    val failedDelete: String,
    val failedDeleteHttp: String,
) {
    fun fehlerText(fehler: EditorFehler, beimLoeschen: Boolean): String = when (fehler) {
        EditorFehler.OrtNichtGefunden -> place
        EditorFehler.NichtErreichbar -> unreachable
        is EditorFehler.Server -> fehler.text
        is EditorFehler.Unerwartet -> {
            val code = fehler.httpCode
            when {
                beimLoeschen && code != null -> failedDeleteHttp.format(code)
                beimLoeschen -> failedDelete
                code != null -> failedSaveHttp.format(code)
                else -> failedSave
            }
        }
    }
}

/** Tausendertrenner in der Anzeige („600.000“); gespeichert bleiben nur die Ziffern. */
private object TausenderTrenner : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                if (i > 0 && (digits.length - i) % 3 == 0) append('.')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (1 until offset).count { (digits.length - it) % 3 == 0 }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it != '.' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
