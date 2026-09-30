package de.immoscrabber.app.core.session

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import de.immoscrabber.app.core.network.TokenPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EncryptedTokenStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After fun tearDown() = scopes.forEach { it.cancel() }

    private fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    /** Eine frische Store-Instanz auf [file] (wie nach einem Neustart der App). */
    private fun store(file: File, aead: () -> Aead): EncryptedTokenStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        return EncryptedTokenStore.create(file, aead, scope)
    }

    /**
     * Beendet alle Store-Instanzen wie ein App-Neustart und wartet darauf: DataStore gibt die
     * Datei erst frei, wenn sein Scope fertig ist; sonst meldet eine neue Instanz auf derselben
     * Datei „mehrere DataStores aktiv“ (flakig in CI).
     */
    private suspend fun restart() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    private val pair = TokenPair("access-geheim", "refresh-geheim")

    @Test
    fun `ohne gespeicherte Tokens ist der Speicher leer`() = runBlocking {
        val file = File(tmp.root, "tokens.pb")

        assertEquals(StoredTokens.None, store(file, ::newAead).read())
    }

    @Test
    fun `gespeicherte Tokens überleben einen Neustart und liegen nicht im Klartext`() = runBlocking {
        val file = File(tmp.root, "tokens.pb")
        val aead = newAead()
        store(file) { aead }.save(pair)
        restart()

        assertEquals(StoredTokens.Present(pair), store(file) { aead }.read())
        val raw = file.readBytes().decodeToString()
        assertFalse(raw.contains("access-geheim"))
        assertFalse(raw.contains("refresh-geheim"))
    }

    @Test
    fun `nach clear ist der Speicher leer`() = runBlocking {
        val file = File(tmp.root, "tokens.pb")
        val aead = newAead()
        val store = store(file) { aead }
        store.save(pair)

        store.clear()

        assertEquals(StoredTokens.None, store.read())
    }

    @Test
    fun `mit anderem Schlüssel ist die Datei unlesbar`() = runBlocking {
        val file = File(tmp.root, "tokens.pb")
        val original = newAead()
        store(file) { original }.save(pair)
        restart()

        val other = newAead()
        assertEquals(StoredTokens.Unreadable, store(file) { other }.read())
    }

    @Test
    fun `ist der Schlüssel nicht verfügbar, gilt der Speicher als unlesbar`() = runBlocking {
        val file = File(tmp.root, "tokens.pb")
        val aead = newAead()
        store(file) { aead }.save(pair)
        restart()

        assertEquals(StoredTokens.Unreadable, store(file) { error("Keystore kaputt") }.read())
    }
}
