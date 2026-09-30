package de.immoscrabber.app.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.io.IOException

/** Server und Username der (letzten) Sitzung, unverschlüsselt (Entscheidung #7). */
data class SessionPrefs(val baseUrl: String?, val username: String?)

interface SessionPrefsStore {
    suspend fun read(): SessionPrefs
    suspend fun save(prefs: SessionPrefs)
}

/** [SessionPrefsStore] auf einem Preferences-DataStore. */
class DataStoreSessionPrefsStore(private val dataStore: DataStore<Preferences>) : SessionPrefsStore {

    override suspend fun read(): SessionPrefs {
        val prefs = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return SessionPrefs(null, null)
        }
        return SessionPrefs(prefs[BASE_URL], prefs[USERNAME])
    }

    override suspend fun save(prefs: SessionPrefs) {
        dataStore.edit {
            if (prefs.baseUrl != null) it[BASE_URL] = prefs.baseUrl else it.remove(BASE_URL)
            if (prefs.username != null) it[USERNAME] = prefs.username else it.remove(USERNAME)
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val USERNAME = stringPreferencesKey("username")
    }
}
