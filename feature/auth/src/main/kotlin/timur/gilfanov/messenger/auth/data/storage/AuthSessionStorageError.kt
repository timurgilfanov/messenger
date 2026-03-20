package timur.gilfanov.messenger.auth.data.storage

/**
 * Errors for local auth session storage operations.
 *
 * - [AccessDenied] - OS-level permission denial for keystore access
 * - [KeystoreUnavailable] - Android Keystore is locked or unavailable
 * - [DataCorrupted] - Decryption failed; ciphertext is tampered or unreadable
 * - [UnknownError] - Unexpected error with preserved cause
 */
sealed interface AuthSessionStorageError {
    data object AccessDenied : AuthSessionStorageError
    data object KeystoreUnavailable : AuthSessionStorageError
    data object DataCorrupted : AuthSessionStorageError
    data class UnknownError(val cause: Throwable) : AuthSessionStorageError
}
