package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveLanguageRepositoryError

/**
 * Errors that can occur during UI language observation operations.
 *
 * Represents failures at the use case layer, combining identity retrieval errors
 * with repository-level language observation errors.
 */
sealed interface ObserveUiLanguageError {
    /**
     * Failed to retrieve current user identity.
     *
     * This error occurs when [IdentityRepository] cannot provide the identity needed
     * to observe the language preference.
     */
    data object Unauthorized : ObserveUiLanguageError

    /**
     * Language observation operation failed at the repository layer.
     *
     * Wraps errors from [ObserveLanguageRepositoryError] such as settings not found,
     * settings conflicts, or settings reset to defaults.
     */
    data class ObserveLanguageRepository(val error: ObserveLanguageRepositoryError) :
        ObserveUiLanguageError
}
