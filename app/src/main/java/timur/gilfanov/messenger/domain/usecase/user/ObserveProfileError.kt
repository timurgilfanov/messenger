package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveProfileRepositoryError

/**
 * Errors that can occur during UI language observation operations.
 *
 * Represents failures at the use case layer, combining identity retrieval errors
 * with repository-level language observation errors.
 */
sealed interface ObserveProfileError {
    /**
     * Failed to retrieve current user identity.
     *
     * This error occurs when [IdentityRepository] cannot provide the identity needed
     * to observe the language preference.
     */
    data object Unauthorized : ObserveProfileError

    /**
     * Profile observation operation failed at the repository layer.
     *
     * Wraps errors from [ObserveProfileRepositoryError].
     */
    data class ObserveProfileRepository(val error: ObserveProfileRepositoryError) :
        ObserveProfileError
}
