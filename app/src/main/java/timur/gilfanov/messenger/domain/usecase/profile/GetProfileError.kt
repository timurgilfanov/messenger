package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.usecase.profile.repository.RepositoryError

/**
 * Errors for profile retrieval operations.
 *
 * Type alias to [RepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias GetProfileError = RepositoryError
