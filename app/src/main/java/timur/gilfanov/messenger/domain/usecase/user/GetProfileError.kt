package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError

/**
 * Errors for profile retrieval operations.
 *
 * Type alias to [RepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias GetProfileError = RepositoryError
