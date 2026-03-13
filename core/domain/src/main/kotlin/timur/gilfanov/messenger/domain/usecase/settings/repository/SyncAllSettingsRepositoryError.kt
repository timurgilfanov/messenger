package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

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
    data class LocalOperationFailed(val error: LocalStorageError) : SyncAllSettingsRepositoryError

    /**
     * Remote batch sync operation failed.
     *
     * This represents a batch-level failure (e.g., network down, authentication failed).
     * Per-setting results (Success/Conflict) are returned in the success case.
     * Wraps the full remote data source error for detailed handling.
     */
    data class RemoteSyncFailed(val error: RemoteError) : SyncAllSettingsRepositoryError
}
