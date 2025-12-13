package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.ObserveProfileRepositoryError

/**
 * Errors that can occur during profile observation operations.
 *
 * Represents failures retrieving profile-related identity or repository data for profile access.
 */
sealed interface ObserveProfileError {
    /**
     * Failed to retrieve current user identity for profile observation.
     *
     * This error occurs when the identity needed for profile observation cannot be retrieved.
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
