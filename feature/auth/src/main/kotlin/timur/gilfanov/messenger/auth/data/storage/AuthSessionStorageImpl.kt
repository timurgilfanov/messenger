package timur.gilfanov.messenger.auth.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

private const val ANDROID_KEY_STORE = "AndroidKeyStore"

private const val TRANSFORMATION = "AES/GCM/NoPadding"

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    runCatching(block).also { result ->
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    }

@Singleton
class AuthSessionStorageImpl @Inject constructor(@ApplicationContext private val context: Context) :
    AuthSessionStorage {

    companion object {
        private const val FILE_NAME = "auth_session"
        private const val KEYSTORE_ALIAS = "auth_session_key"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12
    }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) }
    }

    private val keyAccessToken = stringPreferencesKey("access_token")
    private val keyRefreshToken = stringPreferencesKey("refresh_token")
    private val keyAuthProvider = stringPreferencesKey("auth_provider")

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE,
            )
            val keySpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
    }

    private fun encrypt(plaintext: String): String {
        ensureKeyExists()
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        val encrypted = iv + ciphertext
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        ensureKeyExists()
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null)

        val encrypted = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException(e)
        }
        val iv = encrypted.sliceArray(0 until IV_SIZE)
        val ciphertext = encrypted.sliceArray(IV_SIZE until encrypted.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext)
    }

    override suspend fun getAccessToken(): ResultWithError<String?, AuthSessionStorageError> =
        runCatchingCancellable {
            dataStore.data.first()[keyAccessToken]?.let { decrypt(it) }
        }.toResult()

    override suspend fun getRefreshToken(): ResultWithError<String?, AuthSessionStorageError> =
        runCatchingCancellable {
            dataStore.data.first()[keyRefreshToken]?.let { decrypt(it) }
        }.toResult()

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        AuthSessionStorageError,
        > =
        runCatchingCancellable {
            dataStore.data.first()[keyAuthProvider]?.let {
                runCatchingCancellable { AuthProvider.valueOf(it) }.getOrNull()
            }
        }.toResult()

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, AuthSessionStorageError> = runCatchingCancellable {
        dataStore.edit {
            it[keyAccessToken] = encrypt(tokens.accessToken)
            it[keyRefreshToken] = encrypt(tokens.refreshToken)
        }
        Unit
    }.toResult()

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, AuthSessionStorageError> = runCatchingCancellable {
        dataStore.edit {
            it[keyAccessToken] = encrypt(session.tokens.accessToken)
            it[keyRefreshToken] = encrypt(session.tokens.refreshToken)
            it[keyAuthProvider] = session.provider.name
        }
        Unit
    }.toResult()

    override suspend fun clearSession(): ResultWithError<Unit, AuthSessionStorageError> =
        runCatchingCancellable {
            dataStore.edit { it.clear() }
            Unit
        }.toResult()

    private fun <T> Result<T>.toResult(): ResultWithError<T, AuthSessionStorageError> = fold(
        onSuccess = { ResultWithError.Success(it) },
        onFailure = { e ->
            val error = when (e) {
                // Listed before GeneralSecurityException (its parent) so keystore access failures
                // (getInstance, load, containsAlias, getKey) are not misclassified as DataCorrupted.
                is KeyStoreException -> AuthSessionStorageError.KeystoreUnavailable

                // Crypto operation failed: BadPaddingException from cipher.doFinal() means the
                // ciphertext is tampered or was encrypted with a different key.
                is GeneralSecurityException -> AuthSessionStorageError.DataCorrupted

                // OS-level permission denial for keystore access, unrelated to crypto state.
                is SecurityException -> AuthSessionStorageError.AccessDenied

                else -> AuthSessionStorageError.UnknownError(e)
            }
            ResultWithError.Failure(error)
        },
    )
}
