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
 * - [Repository] - Wraps common failures defined in [RepositoryError]
 */
sealed interface RemovePictureRepositoryError {
    /**
     * The specified picture does not exist or was already removed.
     */
    data object PictureNotFound : RemovePictureRepositoryError

    /**
     * Common repository errors expressed in the shared taxonomy.
     *
     * @property error The underlying [RepositoryError] instance
     */
    data class Repository(val error: RepositoryError) : RemovePictureRepositoryError
}
