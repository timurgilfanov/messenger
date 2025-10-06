package timur.gilfanov.messenger.domain.usecase.user

/**
 * Errors specific to UI language change operations.
 *
 * Defines operation-specific errors for language preference changes, in addition
 * to common errors from [UserOperationError].
 *
 * ## Operation-Specific Errors
 * - [LanguageNotChangedForAllDevices] - Partial synchronization failure
 *
 * ## Inherited Errors
 * - Network/Service errors ([UserOperationError.ServiceUnavailable])
 * - Rate limiting ([UserOperationError.RateLimitExceeded])
 * - Authentication errors ([UserOperationError.Unauthorized])
 */
sealed interface ChangeUiLanguageError : UserOperationError {
    /**
     * Language preference was updated on some devices but not all.
     *
     * This indicates a partial success where the primary device was updated
     * successfully, but synchronization to other devices failed. The user's
     * current device will reflect the new language, but other devices may
     * still show the old language until they can be synced.
     */
    data object LanguageNotChangedForAllDevices : ChangeUiLanguageError
}
