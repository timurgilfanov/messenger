package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

/**
 * Errors that can occur during setting sync operations.
 *
 * ## Identity Errors
 * - [IdentityNotAvailable] - Failed to retrieve current user identity
 *
 * ## Logical Errors
 * - [SettingNotFound] - Setting was not found in local storage
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteSyncFailed] - Remote sync operation failed
 */
sealed interface SyncSettingError {
    /**
     * Failed to retrieve current user identity.
     */
    data object IdentityNotAvailable : SyncSettingError

    /**
     * Setting was not found in local storage during sync preparation.
     */
    data object SettingNotFound : SyncSettingError

    /**
     * Local storage operation failed during sync.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : SyncSettingError

    /**
     * Remote sync operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteSyncFailed(val error: RemoteError) : SyncSettingError
}

/**
 * Maps a [SyncSettingRepositoryError] to the corresponding [SyncSettingError].
 */
internal fun SyncSettingRepositoryError.toUseCaseError(): SyncSettingError = when (this) {
    is SyncSettingRepositoryError.SettingNotFound -> SyncSettingError.SettingNotFound
    is SyncSettingRepositoryError.LocalOperationFailed -> SyncSettingError.LocalOperationFailed(
        error,
    )
    is SyncSettingRepositoryError.RemoteSyncFailed -> SyncSettingError.RemoteSyncFailed(error)
}
