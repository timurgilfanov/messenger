package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.usecase.user.repository.UserRepositoryError

/**
 * Errors specific to profile retrieval operations.
 *
 * ## Repository Errors
 * - [RepositoryError] - Wraps repository layer errors
 */
sealed interface GetProfileError {
    /**
     * Repository layer errors.
     *
     * @property error The underlying repository error
     */
    data class RepositoryError(val error: UserRepositoryError) : GetProfileError
}
