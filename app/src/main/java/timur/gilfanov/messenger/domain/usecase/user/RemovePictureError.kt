package timur.gilfanov.messenger.domain.usecase.user

/**
 * Errors specific to profile picture removal operations.
 *
 * Defines operation-specific errors for picture removal, in addition to
 * common errors from [UserOperationError].
 *
 * ## Operation-Specific Errors
 * - [PictureNotFound] - Picture doesn't exist or was already removed
 *
 * ## Inherited Errors
 * - Network/Service errors ([UserOperationError.ServiceUnavailable])
 * - Rate limiting ([UserOperationError.RateLimitExceeded])
 * - Cooldown restrictions ([UserOperationError.CooldownActive])
 * - Authentication errors ([UserOperationError.Unauthorized])
 */
sealed interface RemovePictureError : UserOperationError {
    /**
     * The specified picture does not exist or was already removed.
     *
     * This can occur if:
     * - Another client/device already removed the picture
     * - The picture URI is outdated or invalid
     * - The user never had a profile picture set
     */
    data object PictureNotFound : RemovePictureError
}
