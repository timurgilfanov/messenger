package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Base error interface for user-related repository operations.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - Target user does not exist
 *
 * ## Common Repository Errors
 * - [Repository] - Wraps common repository errors like network issues, access control, etc.
 */
sealed interface UserRepositoryError {
    /**
     * The target user does not exist.
     *
     * Indicates the requested user ID is not found in the system.
     */
    data object UserNotFound : UserRepositoryError

    /**
     * Common repository errors that can occur across operations.
     *
     * @property error The underlying repository error
     */
    data class Repository(val error: RepositoryError) : UserRepositoryError
}
