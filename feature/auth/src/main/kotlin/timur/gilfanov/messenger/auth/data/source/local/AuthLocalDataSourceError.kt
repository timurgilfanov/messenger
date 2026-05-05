package timur.gilfanov.messenger.auth.data.source.local

/**
 * Errors for local auth session storage operations.
 *
 * - [AccessDenied] - OS-level permission denial for secure local storage access
 * - [KeystoreUnavailable] - Android Keystore is locked or unavailable
 * - [TemporarilyUnavailable] - Temporary file-system or DataStore I/O failure
 * - [StorageFull] - Device storage is full
 * - [ReadOnly] - Backing storage is read-only
 * - [DataCorrupted] - Stored auth data is invalid or cannot be decrypted
 * - [UnknownError] - Unexpected error with preserved cause
 */
sealed interface AuthLocalDataSourceError {
    data object AccessDenied : AuthLocalDataSourceError
    data object KeystoreUnavailable : AuthLocalDataSourceError
    data object TemporarilyUnavailable : AuthLocalDataSourceError
    data object StorageFull : AuthLocalDataSourceError
    data object ReadOnly : AuthLocalDataSourceError
    data object DataCorrupted : AuthLocalDataSourceError
    data class UnknownError(val cause: Throwable) : AuthLocalDataSourceError
}
