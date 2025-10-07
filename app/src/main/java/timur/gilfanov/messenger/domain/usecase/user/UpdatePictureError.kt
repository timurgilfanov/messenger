package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.UpdatePictureRepositoryError

/**
 * Errors for profile picture update operations.
 *
 * Type alias to [UpdatePictureRepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias UpdatePictureError = UpdatePictureRepositoryError
