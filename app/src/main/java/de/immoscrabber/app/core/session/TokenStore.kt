package de.immoscrabber.app.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.google.crypto.tink.Aead
import de.immoscrabber.app.core.network.TokenPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import kotlin.coroutines.cancellation.CancellationException

/** Inhalt des Token-Speichers. */
sealed interface StoredTokens {
    data object None : StoredTokens
    data class Present(val tokens: TokenPair) : StoredTokens

    /** Datei da, aber nicht entschlüsselbar (Schlüssel weg, Datei kaputt): zählt als Sitzungsende. */
    data object Unreadable : StoredTokens
}

/** Persistenter Speicher für das Token-Paar der Sitzung. */
interface TokenStore {
    suspend fun read(): StoredTokens
    suspend fun save(tokens: TokenPair)
    suspend fun clear()
}

/**
 * Token-Speicher auf DataStore, verschlüsselt mit Tink AEAD (Entscheidung #7). Der
 * AES-256-GCM-Schlüssel kommt in der App aus dem Android Keystore (siehe [SessionCrypto]).
 * `datastore-tink` ist noch nicht stabil, daher der eigene [AeadSerializer].
 */
class EncryptedTokenStore private constructor(
    private val dataStore: DataStore<StoredTokens>,
) : TokenStore {

    override suspend fun read(): StoredTokens = try {
        dataStore.data.first()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        StoredTokens.Unreadable
    }

    override suspend fun save(tokens: TokenPair) {
        dataStore.updateData { StoredTokens.Present(tokens) }
    }

    override suspend fun clear() {
        dataStore.updateData { StoredTokens.None }
    }

    companion object {
        fun create(file: File, aead: () -> Aead, scope: CoroutineScope): EncryptedTokenStore =
            EncryptedTokenStore(
                // OkioStorage statt FileStorage: ersetzt die Datei atomar auch unter Windows (Unit-Tests).
                DataStoreFactory.create(
                    storage = OkioStorage(
                        fileSystem = FileSystem.SYSTEM,
                        serializer = AeadSerializer(aead, associatedData = file.name.encodeToByteArray()),
                        producePath = { file.absoluteFile.toOkioPath() },
                    ),
                    scope = scope,
                ),
            )
    }
}

@Serializable
private data class TokenFile(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * Verschlüsselt den Token-Inhalt als Ganzes. Eine leere Datei heißt „keine Tokens“; alles,
 * was sich nicht entschlüsseln oder lesen lässt, wird zu [StoredTokens.Unreadable].
 */
private class AeadSerializer(
    private val aead: () -> Aead,
    private val associatedData: ByteArray,
) : OkioSerializer<StoredTokens> {
    override val defaultValue: StoredTokens = StoredTokens.None

    override suspend fun readFrom(source: BufferedSource): StoredTokens {
        val bytes = source.readByteArray()
        if (bytes.isEmpty()) return StoredTokens.None
        @Suppress("TooGenericExceptionCaught") // Keystore, Tink und JSON werfen Verschiedenes.
        return try {
            val plain = aead().decrypt(bytes, associatedData)
            val file = Json.decodeFromString<TokenFile>(plain.decodeToString())
            StoredTokens.Present(TokenPair(file.accessToken, file.refreshToken))
        } catch (e: Exception) {
            StoredTokens.Unreadable
        }
    }

    override suspend fun writeTo(t: StoredTokens, sink: BufferedSink) {
        if (t !is StoredTokens.Present) return
        val json = Json.encodeToString(TokenFile(t.tokens.accessToken, t.tokens.refreshToken))
        sink.write(aead().encrypt(json.encodeToByteArray(), associatedData))
    }
}
