package de.immoscrabber.app.properties

import de.immoscrabber.app.core.model.Inserat
import de.immoscrabber.app.core.model.Label
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

// Reine Logik des Detail-Sheets der Wischliste (#14). Wie in InseratFormat stehen die deutschen
// Texte hier und nicht in den String-Ressourcen.

private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * „Gefunden heute / gestern / vor 3 Tagen“, ab 7 Tagen „Gefunden am 28.09.2026“. [createdAt] ist
 * der naive UTC-Zeitstempel des Servers; der Kalendertag zählt in der Zeitzone von [clock].
 * Fehlt er oder ist er unlesbar, `null` (die Zeile fällt weg).
 */
fun formatGefunden(createdAt: String?, clock: Clock): String? {
    val found = parseServerTimestamp(createdAt) ?: return null
    val foundDay = found.atOffset(ZoneOffset.UTC).atZoneSameInstant(clock.zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(foundDay, LocalDate.now(clock))
    return when {
        days <= 0 -> "Gefunden heute"
        days == 1L -> "Gefunden gestern"
        days < 7 -> "Gefunden vor $days Tagen"
        else -> "Gefunden am ${foundDay.format(DATE)}"
    }
}

/**
 * Genauer Zeitpunkt für den Tooltip der Gefunden-Zeile: „Gefunden am 28.09.2026 um 14:32“ in
 * [zone]; fehlt [createdAt] oder ist er unlesbar, `null` (kein Tooltip).
 */
fun formatGefundenGenau(createdAt: String?, zone: ZoneId): String? {
    val found = parseServerTimestamp(createdAt)?.atOffset(ZoneOffset.UTC)?.atZoneSameInstant(zone) ?: return null
    return "Gefunden am ${found.format(DATE)} um ${found.format(TIME)}"
}

private fun parseServerTimestamp(raw: String?): LocalDateTime? {
    val text = raw?.trim()?.replace(' ', 'T')?.takeIf(String::isNotEmpty) ?: return null
    return try {
        LocalDateTime.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Link zum Inserat oder `null`, wenn es keinen gibt („Kein Link zum Inserat“). */
fun inseratLink(inserat: Inserat): String? = inserat.url?.trim()?.takeIf(String::isNotEmpty)

/** Inhalt für das Android-Teilen-Menü: der Link als Text, der Titel als Betreff. */
data class ShareContent(val subject: String?, val text: String)

/** Ohne Link gibt es nichts zu teilen (`null`); dann sind „Inserat öffnen“ und „Teilen“ ausgeblendet. */
fun shareContent(inserat: Inserat): ShareContent? {
    val link = inseratLink(inserat) ?: return null
    return ShareContent(subject = inserat.title?.trim()?.takeIf(String::isNotEmpty), text = link)
}

/** Segmente des Umschalters im Sheet: Neu / Favorit / Archiv. */
val bewertungsSegmente: List<Label> = listOf(Label.UNBEWERTET, Label.INTERESSANT, Label.UNINTERESSANT)

/** Neues Label nach dem Tipp auf ein Segment; das schon gewählte Segment bewertet nicht (`null`). */
fun segmentWechsel(current: Label, selected: Label): Label? = selected.takeIf { it != current }

/**
 * Das Inserat, das das Sheet zeigt, frisch aus der Liste (so folgt der Umschalter dem Label).
 * Hat es die Liste verlassen, `null`: Das Sheet schließt.
 */
fun sheetInserat(items: List<Inserat>, id: String?): Inserat? = id?.let { items.firstOrNull { inserat -> inserat.id == it } }
