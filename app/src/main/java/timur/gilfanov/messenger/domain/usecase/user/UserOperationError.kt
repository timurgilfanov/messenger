package timur.gilfanov.messenger.domain.usecase.user

import kotlin.time.Duration

/**
 * Base error interface for all user-related operations.
 *
 * Provides common error cases that can occur across different user operations
 * (profile updates, settings changes, etc.). Specific operations extend this
 * interface with operation-specific errors.
 *
 * ## Error Categories
 *
 * **Network/Service Errors:**
 * - [ServiceUnavailable] - Transient network or service issues
 * - [RateLimitExceeded] - Service-wide throttling, retry later with backoff
 * - [CooldownActive] - User-specific restriction with countdown
 * - [UnknownServiceError] - New backend error not yet handled by client
 *
 * **Authorization Errors:**
 * - [Unauthorized] - User not authenticated or session expired
 * - [UserNotFound] - Target user doesn't exist
 *
 * **Data Errors:**
 * - [LocalDataCorrupted] - Local storage integrity compromised
 *
 * ## Rate Limiting vs Cooldown
 *
 * **[RateLimitExceeded]:** Service protecting itself from overload
 * - Affects all users or requests from this IP/service
 * - No specific wait time, retry with exponential backoff
 * - Example: "Too many requests, please try again later"
 *
 * **[CooldownActive]:** Business rule enforcement per user
 * - Specific to this user for this operation
 * - Includes precise remaining time for countdown display
 * - Example: "Can change profile picture again in 23 hours"
 *
 * ## Forward Compatibility
 * [UnknownServiceError] handles new backend errors introduced after app release,
 * preventing crashes and allowing graceful degradation.
 */
sealed interface UserOperationError {
    /** Service is temporarily unavailable */
    sealed interface ServiceUnavailable : UserOperationError {
        /** No network connectivity available */
        data object NoConnectivity : ServiceUnavailable

        /** Backend service is down or unreachable */
        data object ServiceDown : ServiceUnavailable

        /** Request timed out */
        data object Timeout : ServiceUnavailable
    }

    /** Too many requests from all users, service-wide throttling */
    data object RateLimitExceeded : UserOperationError

    /** User-specific cooldown restriction with remaining time */
    data class CooldownActive(val remaining: Duration) : UserOperationError

    /** User does not exist */
    data object UserNotFound : UserOperationError

    /** User not authenticated or session expired */
    data object Unauthorized : UserOperationError

    /** Backend returned unrecognized error code (forward compatibility) */
    data class UnknownServiceError(val reason: String) : UserOperationError

    /** Local data storage is corrupted */
    data object LocalDataCorrupted : UserOperationError
}
