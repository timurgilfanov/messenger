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
 * ## Common Errors
 * - [UserRepository] - Wraps user-related repository errors
 */
sealed interface ChangeLanguageRepositoryError {
    /**
     * Language preference was updated on some devices but not all.
     *
     * Indicates partial success where synchronization failed for some devices.
     */
    data object LanguageNotChangedForAllDevices : ChangeLanguageRepositoryError

    /**
     * Common user repository errors.
     *
     * @property error The underlying user repository error
     */
    data class UserRepository(val error: UserRepositoryError) : ChangeLanguageRepositoryError
}
