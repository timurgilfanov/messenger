package timur.gilfanov.messenger.auth.data.source.local

/**
 * Errors for local auth session storage operations.
 *
 * - [AccessDenied] - OS-level permission denial for keystore access
 * - [KeystoreUnavailable] - Android Keystore is locked or unavailable
 * - [DataCorrupted] - Decryption failed; ciphertext is tampered or was encrypted with a different
 * key. Only produced by read operations.
 * - [UnknownError] - Unexpected error with preserved cause
 */
sealed interface LocalAuthDataSourceError {
    data object AccessDenied : LocalAuthDataSourceError
    data object KeystoreUnavailable : LocalAuthDataSourceError
    data object DataCorrupted : LocalAuthDataSourceError
    data class UnknownError(val cause: Throwable) : LocalAuthDataSourceError
}
