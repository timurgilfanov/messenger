package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.UpdateNameRepositoryError

/**
 * Errors for name update operations.
 *
 * Type alias to [UpdateNameRepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias UpdateNameError = UpdateNameRepositoryError
