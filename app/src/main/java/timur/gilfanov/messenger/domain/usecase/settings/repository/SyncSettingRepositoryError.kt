package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Error taxonomy for the "sync setting" repository method.
 *
 * Distinguishes between local storage issues and remote sync failures,
 * while abstracting implementation details into repository-level error categories.
 *
 * When more specific errors or modifications are needed, use composition, not inheritance.
 */
sealed interface SyncSettingRepositoryError {
    /**
     * Setting was not found in local storage during sync preparation.
     *
     * This is the only operation-specific error - occurs only during getSetting().
     */
    data object SettingNotFound : SyncSettingRepositoryError

    /**
     * Local storage operation failed during sync.
     *
     * These errors can occur during either get or upsert operations and are handled
     * uniformly by use cases regardless of which operation failed.
     */
    data class LocalOperationFailed(val error: LocalStorageError) : SyncSettingRepositoryError

    /**
     * Remote sync operation failed.
     *
     * Includes authentication failures, network issues, server errors, and rate limiting.
     * Wraps the full remote data source error for detailed handling.
     */
    data class RemoteSyncFailed(val error: RemoteError) : SyncSettingRepositoryError
}
