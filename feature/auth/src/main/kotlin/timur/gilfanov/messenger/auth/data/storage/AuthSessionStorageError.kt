package timur.gilfanov.messenger.auth.data.storage

/**
 * Errors for local auth session storage operations.
 *
 * - [AccessDenied] - SecurityException from EncryptedSharedPreferences
 * - [KeystoreUnavailable] - Android Keystore is locked or unavailable
 * - [DataCorrupted] - Decryption failed, possibly due to key rotation
 * - [UnknownError] - Unexpected error with preserved cause
 */
sealed interface AuthSessionStorageError {
    data object AccessDenied : AuthSessionStorageError
    data object KeystoreUnavailable : AuthSessionStorageError
    data object DataCorrupted : AuthSessionStorageError
    data class UnknownError(val cause: Throwable) : AuthSessionStorageError
}
