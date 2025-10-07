package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Base error interface for user-related repository operations.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - Target user does not exist
 * - [Unauthenticated] - User session expired or not logged in
 * - [InsufficientPermissions] - User lacks required permissions
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
     * User is not authenticated.
     *
     * Indicates the user's session has expired or they are not logged in.
     * Client should redirect to login/authentication flow.
     */
    data object Unauthenticated : UserRepositoryError

    /**
     * User lacks required permissions for this operation.
     *
     * Indicates the authenticated user does not have the necessary
     * permissions to perform this action. Client should show
     * appropriate permission denied message.
     */
    data object InsufficientPermissions : UserRepositoryError

    /**
     * Common repository errors that can occur across operations.
     *
     * @property error The underlying repository error
     */
    data class Repository(val error: RepositoryError) : UserRepositoryError
}
