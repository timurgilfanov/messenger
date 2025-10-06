package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlin.time.Duration

/**
 * Base error interface for repository layer operations.
 *
 * Provides common error cases that can occur across different repository operations.
 * These errors represent infrastructure concerns (network, storage, service availability)
 * rather than domain validation or business rule violations.
 *
 * ## Error Categories
 *
 * **Network/Service Errors:**
 * - [ServiceUnavailable] - Transient connectivity or service issues
 * - [UnknownServiceError] - Unrecognized backend error for forward compatibility
 *
 * **Access Control:**
 * - [AccessDenied] - Authentication failure or insufficient permissions
 * - [CooldownActive] - User-specific rate limiting with remaining time
 *
 * **Data Integrity:**
 * - [LocalDataCorrupted] - Local storage integrity compromised
 */
sealed interface RepositoryError {
    /**
     * Service is temporarily unavailable.
     *
     * Transient errors that may resolve with retry logic.
     */
    sealed interface ServiceUnavailable : RepositoryError {
        /** No network connectivity available */
        data object NoConnectivity : ServiceUnavailable

        /** Backend service is down or unreachable */
        data object ServiceDown : ServiceUnavailable

        /** Request timed out */
        data object Timeout : ServiceUnavailable
    }

    /**
     * User not authenticated or lacks required permissions.
     *
     * Indicates session expired or user is not authorized for the operation.
     */
    data object AccessDenied : RepositoryError

    /**
     * User-specific cooldown restriction with remaining time.
     *
     * Business rule enforcement preventing too frequent operations.
     * Unlike service-wide rate limiting, this is specific to the user
     * and includes precise remaining time for countdown display.
     *
     * @property remaining Time remaining until operation can be retried
     */
    data class CooldownActive(val remaining: Duration) : RepositoryError

    /**
     * Backend returned unrecognized error code.
     *
     * Forward compatibility mechanism for handling new backend errors
     * introduced after app release, preventing crashes and allowing
     * graceful degradation.
     *
     * @property reason Description of the unknown error
     */
    data class UnknownServiceError(val reason: String) : RepositoryError

    /**
     * Local data storage is corrupted.
     *
     * Indicates integrity issues with cached or persisted data.
     */
    data object LocalDataCorrupted : RepositoryError
}
