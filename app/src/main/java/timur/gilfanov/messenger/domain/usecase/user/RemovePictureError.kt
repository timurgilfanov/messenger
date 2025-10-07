package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.RemovePictureRepositoryError

/**
 * Errors specific to profile picture removal operations.
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Repository Errors
 * - [RepositoryError] - Wraps repository layer errors
 */
sealed interface RemovePictureError {
    /**
     * The specified picture does not exist or was already removed.
     *
     * This can occur if:
     * - Another client/device already removed the picture
     * - The picture URI is outdated or invalid
     * - The user never had a profile picture set
     */
    data object PictureNotFound : RemovePictureError

    /**
     * Repository layer errors.
     *
     * @property error The underlying repository error
     */
    data class RepositoryError(val error: RemovePictureRepositoryError) : RemovePictureError
}
