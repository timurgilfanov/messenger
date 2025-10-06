package timur.gilfanov.messenger.domain.usecase.user.repository

/**
 * Base error interface for user-related repository operations.
 *
 * Extends [RepositoryError] with user-specific error cases.
 *
 * ## User-Specific Errors
 * - [UserNotFound] - Target user does not exist
 *
 * ## Inherited Errors
 * - Network/Service errors ([RepositoryError.ServiceUnavailable])
 * - Access control ([RepositoryError.AccessDenied], [RepositoryError.CooldownActive])
 * - Data integrity ([RepositoryError.LocalDataCorrupted])
 * - Unknown errors ([RepositoryError.UnknownServiceError])
 */
sealed interface UserRepositoryError : RepositoryError {
    /**
     * The target user does not exist.
     *
     * Indicates the requested user ID is not found in the system.
     */
    data object UserNotFound : UserRepositoryError
}
