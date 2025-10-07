package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.UserRepositoryError

/**
 * Errors for profile retrieval operations.
 *
 * Type alias to [UserRepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias GetProfileError = UserRepositoryError
