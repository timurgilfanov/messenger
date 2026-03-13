package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.usecase.profile.repository.RemovePictureRepositoryError

/**
 * Errors for profile picture removal operations.
 *
 * Type alias to [RemovePictureRepositoryError] as use case layer has no unique errors.
 * If use case-specific errors are needed, convert this to a sealed interface.
 */
typealias RemovePictureError = RemovePictureRepositoryError
