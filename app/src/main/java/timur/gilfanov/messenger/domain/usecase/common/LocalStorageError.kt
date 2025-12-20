package timur.gilfanov.messenger.domain.usecase.common

/**
 * Common error taxonomy for local storage operations.
 *
 * These errors can occur during get, insert, update, or delete operations and are handled
 * uniformly by use cases regardless of which operation failed.
 */
sealed interface LocalStorageError {
    /**
     * Transient local storage issue that may resolve on retry.
     *
     * Maps from data source errors: ConcurrentModificationError, DiskIOError.
     */
    data object TemporarilyUnavailable : LocalStorageError

    /**
     * Local storage is full and cannot complete the operation.
     *
     * Can occur on reads (Room logging) or writes. Requires freeing storage space.
     */
    data object StorageFull : LocalStorageError

    /**
     * Local database is corrupted and cannot be accessed reliably.
     *
     * Maps from data source error: DatabaseCorrupted. Requires clearing app data.
     */
    data object Corrupted : LocalStorageError

    /**
     * Local database is in read-only mode and cannot accept writes.
     *
     * Maps from data source error: ReadOnlyDatabase. May require app restart or clearing data.
     */
    data object ReadOnly : LocalStorageError

    /**
     * Insufficient permissions to access local storage.
     *
     * Maps from data source error: AccessDenied. Requires granting storage permissions.
     */
    data object AccessDenied : LocalStorageError

    /**
     * An unknown error occurred during local storage operation.
     *
     * Maps from data source error: UnknownError. Contains the original cause for diagnostics.
     */
    data class UnknownError(val cause: Throwable) : LocalStorageError
}
