package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Error taxonomy for the "sync all settings" repository method.
 *
 * Distinguishes between local storage issues and remote batch sync failures,
 * while abstracting implementation details into repository-level error categories.
 *
 * When more specific errors or modifications are needed, use composition, not inheritance.
 */
sealed interface SyncAllSettingsRepositoryError {
    /**
     * Local storage operation failed during batch sync.
     *
     * These errors can occur during getUnsyncedSettings() or upsert() operations
     * and are handled uniformly by use cases regardless of which operation failed.
     */
    sealed interface LocalStorageError : SyncAllSettingsRepositoryError {
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
         * Insufficient permissions to access local storage.
         *
         * Maps from data source error: AccessDenied. Requires granting storage permissions.
         */
        data object AccessDenied : LocalStorageError

        /**
         * Local database is in read-only mode and cannot accept writes.
         *
         * Maps from data source error: ReadOnlyDatabase. May require app restart or clearing data.
         */
        data object ReadOnly : LocalStorageError

        /**
         * An unknown error occurred during local storage operation.
         *
         * Maps from data source error: UnknownError. Contains the original cause for diagnostics.
         */
        data class UnknownError(val cause: Throwable) : LocalStorageError
    }

    /**
     * Remote batch sync operation failed.
     *
     * This represents a batch-level failure (e.g., network down, authentication failed).
     * Per-setting results (Success/Conflict) are returned in the success case.
     * Wraps the full remote data source error for detailed handling.
     */
    data class RemoteSyncFailed(val error: RepositoryError) : SyncAllSettingsRepositoryError
}
