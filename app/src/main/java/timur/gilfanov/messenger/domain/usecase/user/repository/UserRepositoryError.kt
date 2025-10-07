package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Base error interface for user-related repository operations.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - Target user does not exist
 * - [AccessDenied] - User not authenticated or lacks permissions
 *
 * ## Common Repository Errors
 * - [Repository] - Wraps common repository errors like network issues, cooldowns, etc.
 */
sealed interface UserRepositoryError {
    /**
     * The target user does not exist.
     *
     * Indicates the requested user ID is not found in the system.
     */
    data object UserNotFound : UserRepositoryError

    /**
     * User not authenticated or lacks required permissions.
     *
     * Indicates session expired or user is not authorized for the operation.
     */
    data object AccessDenied : UserRepositoryError

    /**
     * Common repository errors that can occur across operations.
     *
     * @property error The underlying repository error
     */
    data class Repository(val error: RepositoryError) : UserRepositoryError
}
