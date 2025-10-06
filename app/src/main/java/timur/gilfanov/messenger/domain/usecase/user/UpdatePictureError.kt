package timur.gilfanov.messenger.domain.usecase.user

/**
 * Errors specific to profile picture update operations.
 *
 * Defines validation errors that can occur when uploading a new profile picture,
 * in addition to common errors from [UserOperationError].
 *
 * ## Validation Errors
 * - [FileSizeOutOfBounds] - Image file too small or too large
 * - [WidthOutOfBounds] - Image width doesn't meet requirements
 * - [HeightOutOfBounds] - Image height doesn't meet requirements
 * - [PlatformPolicyViolation] - Image violates content policy
 *
 * ## Inherited Errors
 * - Network/Service errors ([UserOperationError.ServiceUnavailable])
 * - Rate limiting ([UserOperationError.RateLimitExceeded])
 * - Cooldown restrictions ([UserOperationError.CooldownActive])
 * - Authentication errors ([UserOperationError.Unauthorized])
 */
sealed interface UpdatePictureError : UserOperationError {
    /**
     * Picture file size does not meet requirements.
     *
     * @property size Actual file size in bytes
     * @property min Minimum allowed file size in bytes
     * @property max Maximum allowed file size in bytes
     */
    data class FileSizeOutOfBounds(val size: Long, val min: Long, val max: Long) :
        UpdatePictureError

    /**
     * Picture width does not meet requirements.
     *
     * @property width Actual width in pixels
     * @property min Minimum allowed width in pixels
     * @property max Maximum allowed width in pixels
     */
    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) : UpdatePictureError

    /**
     * Picture height does not meet requirements.
     *
     * @property height Actual height in pixels
     * @property min Minimum allowed height in pixels
     * @property max Maximum allowed height in pixels
     */
    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) : UpdatePictureError

    /**
     * Picture violates platform content policy.
     *
     * These errors indicate that the image contains inappropriate content
     * detected by platform moderation systems.
     */
    sealed interface PlatformPolicyViolation : UpdatePictureError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }
}
