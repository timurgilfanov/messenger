package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError

/**
 * Errors that can occur during UI language change operations.
 *
 * ## Identity Errors
 * - [Unauthorized] - Failed to retrieve current user identity
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
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
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : ChangeUiLanguageError
}

/**
 * Maps a [ChangeLanguageRepositoryError] to the corresponding [ChangeUiLanguageError].
 */
internal fun ChangeLanguageRepositoryError.toUseCaseError(): ChangeUiLanguageError = when (this) {
    is ChangeLanguageRepositoryError.LocalOperationFailed ->
        ChangeUiLanguageError.LocalOperationFailed(error)
}
