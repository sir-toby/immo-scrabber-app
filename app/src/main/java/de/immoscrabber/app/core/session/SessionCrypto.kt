package de.immoscrabber.app.core.session

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Tink-AEAD (AES-256-GCM) für den Token-Speicher. Das Keyset liegt in eigenen
 * SharedPreferences und ist selbst mit einem Master-Key aus dem Android Keystore
 * verschlüsselt (Entscheidung #7). Keyset-Datei und Token-Datei sind vom Backup
 * ausgeschlossen (`backup_rules.xml`, `data_extraction_rules.xml`).
 */
object SessionCrypto {
    /** Name der SharedPreferences-Datei mit dem Keyset; muss zu den Backup-Regeln passen. */
    const val KEYSET_PREFS = "session_keyset"
    private const val KEYSET_NAME = "session_tokens_keyset"
    private const val MASTER_KEY_URI = "android-keystore://immo_session_master_key"

    /** Erzeugt beim ersten Aufruf Keyset und Master-Key. Blockiert (Keystore), nicht auf Main rufen. */
    fun keystoreAead(context: Context): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
}
