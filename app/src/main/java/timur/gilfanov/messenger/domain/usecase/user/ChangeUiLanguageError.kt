package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError

/**
 * Errors that can occur during UI language change operations.
 *
 * Represents failures at the use case layer, combining identity retrieval errors
 * with repository-level language change errors.
 */
sealed interface ChangeUiLanguageError {
    /**
     * Failed to retrieve current user identity.
     *
     * This error occurs when [IdentityRepository] cannot provide the identity needed
     * to perform the language change operation.
     */
    data object Unauthorized : ChangeUiLanguageError

    /**
     * Language change operation failed at the repository layer.
     *
     * Wraps errors from [ChangeLanguageRepositoryError] such as network failures,
     * local storage issues, or backup synchronization problems.
     */
    data class ChangeLanguageRepository(val error: ChangeLanguageRepositoryError) :
        ChangeUiLanguageError
}
