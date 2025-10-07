package timur.gilfanov.messenger.data.source.remote

/**
 * Errors specific to picture update remote operations.
 *
 * Defines validation errors returned by the backend when uploading pictures.
 *
 * ## Validation Errors
 * - [FileSizeOutOfBounds] - Image file too small or too large
 * - [WidthOutOfBounds] - Image width doesn't meet requirements
 * - [HeightOutOfBounds] - Image height doesn't meet requirements
 * - [PlatformPolicyViolation] - Image violates content policy
 *
 * ## Common Errors
 * - [RemoteUser] - Wraps remote user data source errors
 */
sealed interface UpdatePictureRemoteDataSourceError {
    /**
     * Picture file size does not meet requirements.
     *
     * @property size Actual file size in bytes
     * @property min Minimum allowed file size in bytes
     * @property max Maximum allowed file size in bytes
     */
    data class FileSizeOutOfBounds(val size: Long, val min: Long, val max: Long) :
        UpdatePictureRemoteDataSourceError

    /**
     * Picture width does not meet requirements.
     *
     * @property width Actual width in pixels
     * @property min Minimum allowed width in pixels
     * @property max Maximum allowed width in pixels
     */
    data class WidthOutOfBounds(val width: Int, val min: Int, val max: Int) :
        UpdatePictureRemoteDataSourceError

    /**
     * Picture height does not meet requirements.
     *
     * @property height Actual height in pixels
     * @property min Minimum allowed height in pixels
     * @property max Maximum allowed height in pixels
     */
    data class HeightOutOfBounds(val height: Int, val min: Int, val max: Int) :
        UpdatePictureRemoteDataSourceError

    /**
     * Picture violates platform content policy.
     */
    sealed interface PlatformPolicyViolation : UpdatePictureRemoteDataSourceError {
        data object Pornography : PlatformPolicyViolation
        data object Violence : PlatformPolicyViolation
        data object IllegalSubstance : PlatformPolicyViolation
    }

    /**
     * Common remote user data source errors.
     *
     * @property error The underlying remote user data source error
     */
    data class RemoteUser(val error: RemoteUserDataSourceError) : UpdatePictureRemoteDataSourceError
}
