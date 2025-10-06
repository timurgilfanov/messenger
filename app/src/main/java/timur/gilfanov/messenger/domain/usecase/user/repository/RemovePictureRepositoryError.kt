package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Errors specific to picture removal repository operations.
 *
 * Defines operation-specific errors returned by the repository layer
 * when removing pictures.
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Inherited Errors
 * Inherits all errors from [UserRepositoryError] and [RepositoryError].
 */
sealed interface RemovePictureRepositoryError : UserRepositoryError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRepositoryError
}
