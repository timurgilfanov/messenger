package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError

/**
 * Errors that can occur during batch settings sync operations.
 *
 * ## Identity Errors
 * - [IdentityNotAvailable] - Failed to retrieve current user identity
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteSyncFailed] - Remote sync operation failed
 */
sealed interface SyncAllPendingSettingsError {
    /**
     * Failed to retrieve current user identity.
     */
    data object IdentityNotAvailable : SyncAllPendingSettingsError

    /**
     * Local storage operation failed during batch sync.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : SyncAllPendingSettingsError

    /**
     * Remote batch sync operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteSyncFailed(val error: RemoteError) : SyncAllPendingSettingsError
}

/**
 * Maps a [SyncAllSettingsRepositoryError] to the corresponding [SyncAllPendingSettingsError].
 */
internal fun SyncAllSettingsRepositoryError.toUseCaseError(): SyncAllPendingSettingsError =
    when (this) {
        is SyncAllSettingsRepositoryError.LocalOperationFailed ->
            SyncAllPendingSettingsError.LocalOperationFailed(error)
        is SyncAllSettingsRepositoryError.RemoteSyncFailed ->
            SyncAllPendingSettingsError.RemoteSyncFailed(error)
    }
