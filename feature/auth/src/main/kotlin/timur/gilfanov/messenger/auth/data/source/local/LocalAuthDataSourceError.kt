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
sealed interface LocalAuthDataSourceError {
    data object AccessDenied : LocalAuthDataSourceError
    data object KeystoreUnavailable : LocalAuthDataSourceError
    data object TemporarilyUnavailable : LocalAuthDataSourceError
    data object StorageFull : LocalAuthDataSourceError
    data object ReadOnly : LocalAuthDataSourceError
    data object DataCorrupted : LocalAuthDataSourceError
    data class UnknownError(val cause: Throwable) : LocalAuthDataSourceError
}
