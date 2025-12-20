package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.usecase.profile.repository.UpdateNameRepositoryError

/**
 * Errors for name update operations.
 *
 * Type alias to [UpdateNameRepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias UpdateNameError = UpdateNameRepositoryError
