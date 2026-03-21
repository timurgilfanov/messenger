package timur.gilfanov.messenger.auth.data.source.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEYSTORE_ALIAS = "auth_session_key"
private const val KEY_SIZE_BITS = 256
private const val GCM_TAG_LENGTH = 128
private const val IV_SIZE = 12

@Singleton
class AuthSessionCipherImpl @Inject constructor() : AuthSessionCipher {

    override fun encrypt(plaintext: String): ResultWithError<String, AuthSessionCipherError> =
        ensureKeyExists().fold(
            onSuccess = {
                loadSecretKey().fold(
                    onSuccess = { secretKey ->
                        createEncryptCipher(secretKey).fold(
                            onSuccess = { cipher ->
                                encryptValue(cipher, plaintext)
                            },
                            onFailure = { ResultWithError.Failure(it) },
                        )
                    },
                    onFailure = { ResultWithError.Failure(it) },
                )
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    override fun decrypt(encoded: String): ResultWithError<String, AuthSessionCipherError> =
        ensureKeyExists().fold(
            onSuccess = {
                loadSecretKey().fold(
                    onSuccess = { secretKey ->
                        decodePayload(encoded).fold(
                            onSuccess = { encrypted ->
                                createDecryptCipher(secretKey, encrypted).fold(
                                    onSuccess = { cipher ->
                                        decryptValue(cipher, encrypted)
                                    },
                                    onFailure = { ResultWithError.Failure(it) },
                                )
                            },
                            onFailure = { ResultWithError.Failure(it) },
                        )
                    },
                    onFailure = { ResultWithError.Failure(it) },
                )
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    private fun ensureKeyExists(): ResultWithError<Unit, AuthSessionCipherError> =
        loadKeyStore().fold(
            onSuccess = { keyStore ->
                keyStore.containsCipherAlias().fold(
                    onSuccess = { exists ->
                        if (exists) {
                            ResultWithError.Success(Unit)
                        } else {
                            generateKey()
                        }
                    },
                    onFailure = { ResultWithError.Failure(it) },
                )
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    private fun loadSecretKey(): ResultWithError<SecretKey, AuthSessionCipherError> =
        loadKeyStore().fold(
            onSuccess = { keyStore ->
                runCatching<ResultWithError<SecretKey, AuthSessionCipherError>> {
                    val key = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                    if (key == null) {
                        ResultWithError.Failure(AuthSessionCipherError.KeystoreUnavailable)
                    } else {
                        ResultWithError.Success(key)
                    }
                }.getOrElse { error ->
                    ResultWithError.Failure(error.toCipherError())
                }
            },
            onFailure = { ResultWithError.Failure(it) },
        )

    private fun loadKeyStore(): ResultWithError<KeyStore, AuthSessionCipherError> =
        runCipherOperation {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            keyStore
        }

    private fun generateKey(): ResultWithError<Unit, AuthSessionCipherError> = runCipherOperation {
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
        Unit
    }

    private fun createEncryptCipher(
        secretKey: SecretKey,
    ): ResultWithError<Cipher, AuthSessionCipherError> = runCipherOperation {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }

    private fun encryptValue(
        cipher: Cipher,
        plaintext: String,
    ): ResultWithError<String, AuthSessionCipherError> = runCipherOperation {
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun createDecryptCipher(
        secretKey: SecretKey,
        encrypted: ByteArray,
    ): ResultWithError<Cipher, AuthSessionCipherError> {
        if (encrypted.size < IV_SIZE) {
            return ResultWithError.Failure(AuthSessionCipherError.DataCorrupted)
        }
        return runCipherOperation {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, encrypted.copyOfRange(0, IV_SIZE))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher
        }
    }

    private fun decryptValue(
        cipher: Cipher,
        encrypted: ByteArray,
    ): ResultWithError<String, AuthSessionCipherError> =
        runCatching<ResultWithError<String, AuthSessionCipherError>> {
            val plaintext = cipher.doFinal(encrypted.copyOfRange(IV_SIZE, encrypted.size))
            ResultWithError.Success(String(plaintext, Charsets.UTF_8))
        }.getOrElse { error ->
            when (error) {
                is GeneralSecurityException ->
                    ResultWithError.Failure(AuthSessionCipherError.DataCorrupted)
                else -> ResultWithError.Failure(error.toCipherError())
            }
        }
}

private fun decodePayload(encoded: String): ResultWithError<ByteArray, AuthSessionCipherError> =
    runCatching<ByteArray> {
        Base64.decode(encoded, Base64.NO_WRAP)
    }.fold(
        onSuccess = { ResultWithError.Success(it) },
        onFailure = { ResultWithError.Failure(AuthSessionCipherError.DataCorrupted) },
    )

private fun KeyStore.containsCipherAlias(): ResultWithError<Boolean, AuthSessionCipherError> =
    runCipherOperation { containsAlias(KEYSTORE_ALIAS) }

private fun <T> runCipherOperation(action: () -> T): ResultWithError<T, AuthSessionCipherError> =
    runCatching(action).fold(
        onSuccess = { ResultWithError.Success(it) },
        onFailure = { ResultWithError.Failure(it.toCipherError()) },
    )

private fun Throwable.toCipherError(): AuthSessionCipherError = when (this) {
    is KeyStoreException -> AuthSessionCipherError.KeystoreUnavailable
    is GeneralSecurityException -> AuthSessionCipherError.KeystoreUnavailable
    is SecurityException -> AuthSessionCipherError.AccessDenied
    else -> AuthSessionCipherError.Unexpected(this)
}
