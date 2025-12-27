package timur.gilfanov.messenger.domain.usecase.profile.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors specific to picture removal repository operations.
 *
 * Defines operation-specific errors returned by the repository layer
 * when removing pictures.
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface RemovePictureRepositoryError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : RemovePictureRepositoryError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : RemovePictureRepositoryError
}
