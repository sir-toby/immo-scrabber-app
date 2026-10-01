package de.immoscrabber.app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import de.immoscrabber.app.R
import de.immoscrabber.app.core.model.PropertyType

/**
 * `POST_NOTIFICATIONS` wird genau einmal gefragt, nach dem ersten erfolgreichen Login
 * (Entscheidung #9). Vor Android 13 gibt es die Laufzeit-Berechtigung nicht.
 */
fun sollBenachrichtigungenAnfragen(sdkInt: Int, schonGefragt: Boolean, erteilt: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !schonGefragt && !erteilt

/** Android-Seite der Benachrichtigungen über Neuzugänge: Channels, Anzeige, Berechtigung. */
class Benachrichtigungen(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Ein Channel je Typ („Neue Häuser“ …); an/aus und Ton je Typ regelt das System. */
    fun channelsAnlegen() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            PropertyType.entries.map { type ->
                NotificationChannel(
                    BenachrichtigungsInhalt.channelId(type),
                    context.getString(channelName(type)),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            },
        )
    }

    /** App-Benachrichtigungen erlaubt (Berechtigung erteilt und nicht in den Systemeinstellungen aus). */
    fun erlaubt(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Einmal nach dem ersten Login fragen; danach nie wieder ([alsGefragtMerken]). */
    fun sollAnfragen(): Boolean = sollBenachrichtigungenAnfragen(
        sdkInt = Build.VERSION.SDK_INT,
        schonGefragt = prefs.getBoolean(KEY_GEFRAGT, false),
        erteilt = berechtigungErteilt(),
    )

    fun alsGefragtMerken() {
        prefs.edit { putBoolean(KEY_GEFRAGT, true) }
    }

    /** Die System-Einstellungsseite der App für Benachrichtigungen. */
    fun systemEinstellungenIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Zeigt (oder ersetzt) die Benachrichtigung des Typs. Monochromes Icon, kein Bild; Tippen
     * öffnet die App mit ihrem normalen Startziel.
     */
    fun zeigen(inhalt: BenachrichtigungsInhalt) {
        if (!berechtigungErteilt()) return
        val start = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val tippen = PendingIntent.getActivity(
            context,
            inhalt.notificationId,
            start,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stil = NotificationCompat.InboxStyle().setBigContentTitle(inhalt.titel)
        inhalt.zeilen.forEach(stil::addLine)
        val notification = NotificationCompat.Builder(context, inhalt.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_blue))
            .setContentTitle(inhalt.titel)
            .setContentText(inhalt.zeilen.firstOrNull())
            .setStyle(stil)
            .setContentIntent(tippen)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(inhalt.notificationId, notification)
        } catch (_: SecurityException) {
            // Berechtigung zwischen Prüfung und Anzeige entzogen: dann eben nichts.
        }
    }

    private fun berechtigungErteilt(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun channelName(type: PropertyType): Int = when (type) {
        PropertyType.HOUSE -> R.string.notification_channel_houses
        PropertyType.FLAT -> R.string.notification_channel_flats
        PropertyType.SITE -> R.string.notification_channel_sites
    }

    private companion object {
        const val PREFS_FILE = "push"
        const val KEY_GEFRAGT = "post_notifications_requested"
    }
}
