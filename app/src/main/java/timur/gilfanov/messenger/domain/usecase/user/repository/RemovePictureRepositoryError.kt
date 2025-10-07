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
 * ## Common Errors
 * - [UserRepository] - Wraps user-related repository errors
 */
sealed interface RemovePictureRepositoryError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRepositoryError

    /**
     * Common user repository errors.
     *
     * @property error The underlying user repository error
     */
    data class UserRepository(val error: UserRepositoryError) : RemovePictureRepositoryError
}
