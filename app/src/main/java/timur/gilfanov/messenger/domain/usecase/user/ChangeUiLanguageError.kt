package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError

/**
 * Errors for UI language change operations.
 *
 * Type alias to [ChangeLanguageRepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias ChangeUiLanguageError = ChangeLanguageRepositoryError
