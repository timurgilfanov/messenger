package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Errors specific to language change repository operations.
 *
 * Defines operation-specific errors returned by the repository layer
 * when changing language preferences.
 *
 * ## Operation-Specific Errors
 * - [LanguageNotChangedForAllDevices] - Partial synchronization failure
 *
 * ## Inherited Errors
 * Inherits all errors from [UserRepositoryError] and [RepositoryError].
 */
sealed interface ChangeLanguageRepositoryError : UserRepositoryError {
    /**
     * Language preference was updated on some devices but not all.
     *
     * Indicates partial success where synchronization failed for some devices.
     */
    data object LanguageNotChangedForAllDevices : ChangeLanguageRepositoryError
}
