package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Errors specific to picture update repository operations.
 *
 * Defines validation errors returned by the repository layer when updating pictures.
 *
 * ## Validation Errors
 * - [FileSizeOutOfBounds] - Image file too small or too large
 * - [WidthOutOfBounds] - Image width doesn't meet requirements
 * - [HeightOutOfBounds] - Image height doesn't meet requirements
 * - [PlatformPolicyViolation] - Image violates content policy
 *
 * ## Common Errors
 * - [UserRepository] - Wraps user-related repository errors
 */
sealed interface UpdatePictureRepositoryError {
    /**
     * Picture file size does not meet requirements.
     *
     * @property size Actual file size in bytes
     * @property min Minimum allowed file size in bytes
     * @property max Maximum allowed file size in bytes
     */
    data class FileSizeOutOfBounds(val size: Long, val min: Long, val max: Long) :
        UpdatePictureRepositoryError

    /**
     * Picture width does not meet requirements.
     *
     * @property width Actual width in pixels
     * @property min Minimum allowed width in pixels
     * @property max Maximum allowed width in pixels
     */
    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) :
        UpdatePictureRepositoryError

    /**
     * Picture height does not meet requirements.
     *
     * @property height Actual height in pixels
     * @property min Minimum allowed height in pixels
     * @property max Maximum allowed height in pixels
     */
    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) :
        UpdatePictureRepositoryError

    /**
     * Picture violates platform content policy.
     */
    sealed interface PlatformPolicyViolation : UpdatePictureRepositoryError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    /**
     * Common user repository errors.
     *
     * @property error The underlying user repository error
     */
    data class UserRepository(val error: UserRepositoryError) : UpdatePictureRepositoryError
}
