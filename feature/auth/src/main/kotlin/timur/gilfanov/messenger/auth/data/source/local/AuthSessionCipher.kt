package timur.gilfanov.messenger.auth.data.source.local

import timur.gilfanov.messenger.domain.entity.ResultWithError

/**
 * Encrypts and decrypts auth session values for local persistence.
 */
interface AuthSessionCipher {
    /**
     * Encrypts plaintext for secure storage.
     */
    fun encrypt(plaintext: String): ResultWithError<String, AuthSessionCipherError>

    /**
     * Decrypts a previously stored payload.
     *
     * Returns [AuthSessionCipherError.DataCorrupted] when the payload is invalid or tampered with.
     */
    fun decrypt(encoded: String): ResultWithError<String, AuthSessionCipherError>
}

/**
 * Errors produced while encrypting or decrypting auth session values.
 *
 * - [AccessDenied] - OS-level permission denial for keystore access
 * - [KeystoreUnavailable] - Android Keystore is locked or unavailable
 * - [DataCorrupted] - Stored encrypted payload is invalid or cannot be decrypted
 * - [UnknownError] - Unexpected error with preserved cause
 */
sealed interface AuthSessionCipherError {
    data object AccessDenied : AuthSessionCipherError
    data object KeystoreUnavailable : AuthSessionCipherError
    data object DataCorrupted : AuthSessionCipherError
    data class UnknownError(val cause: Throwable) : AuthSessionCipherError
}
