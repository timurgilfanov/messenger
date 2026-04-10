package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError

/**
 * Errors that can occur during UI language change operations.
 *
 * ## Auth Errors
 * - [Unauthorized] - Not authenticated
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface ChangeUiLanguageError {
    /**
     * Not authenticated.
     *
     * This error occurs when [timur.gilfanov.messenger.domain.usecase.auth.AuthRepository] cannot
     * provide an authenticated session needed to perform the language change operation.
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
