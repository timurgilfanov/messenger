package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason

/**
 * Common errors for remote data source operations.
 *
 * Focused on network and server-level failures during remote API calls.
 * Entity-specific errors (e.g., UserNotFound) are defined in separate
 * interfaces (e.g., RemoteSettingsDataSourceError) and compose this interface
 * for common infrastructure errors.
 *
 * ## Migration from RemoteDataSourceError
 *
 * This interface replaces [RemoteDataSourceError] with better separation of concerns:
 *
 * ### Errors moved to entity-specific interfaces:
 * - ChatNotFound, MessageNotFound → RemoteChatDataSourceError, RemoteMessageDataSourceError
 *
 * ### Errors moved to operation-specific interfaces:
 * - InvalidInviteLink, ExpiredInviteLink → Join chat operation errors
 * - ChatClosed, AlreadyJoined, ChatFull → Join chat operation errors
 * - UserBlocked → Operation-specific error types
 *
 * ### Errors moved to auth-specific interface:
 * - Unauthorized → RemoteAuthDataSourceError or similar
 *
 * ### Errors kept in V2 (infrastructure errors):
 * - NetworkNotAvailable, ServerUnreachable → ServiceUnavailable hierarchy
 * - ServerError → ServerError
 * - RateLimitExceeded → RateLimitExceeded
 * - CooldownActive → CooldownActive
 * - UnknownError → UnknownServiceError
 */
sealed interface RemoteDataSourceErrorV2 {
    /**
     * Service is unavailable or unreachable.
     *
     * Indicates network-level failures preventing communication with the server.
     * These errors are typically transient and may succeed on retry.
     */
    sealed interface ServiceUnavailable : RemoteDataSourceErrorV2 {
        /**
         * No network connectivity available.
         *
         * The device has no active network connection (WiFi, cellular, etc.).
         */
        data object NetworkNotAvailable : ServiceUnavailable

        /**
         * Server cannot be reached.
         *
         * Network is available but the server is not responding.
         * Common causes include DNS failures or server downtime.
         */
        data object ServerUnreachable : ServiceUnavailable

        /**
         * Request timed out.
         *
         * The server did not respond within the configured timeout period.
         */
        data object Timeout : ServiceUnavailable
    }

    /**
     * Server encountered an internal error.
     *
     * The server returned a 5xx status code indicating an error on the server side.
     * These errors may be transient and could succeed on retry.
     */
    data object ServerError : RemoteDataSourceErrorV2

    /**
     * Rate limit exceeded.
     *
     * The client has exceeded the general rate limit that applies to all users.
     * This is a global throttling mechanism to protect server resources and
     * ensure fair usage across all clients.
     */
    data object RateLimitExceeded : RemoteDataSourceErrorV2

    /**
     * Operation is in cooldown period.
     *
     * The server has placed this operation in cooldown and it cannot be
     * repeated until the cooldown period expires. The server may enforce
     * cooldowns for various reasons including abuse prevention, suspicious
     * activity detection, or rate limiting specific operations.
     *
     * @property remaining Time remaining until the operation can be performed again
     */
    data class CooldownActive(val remaining: Duration) : RemoteDataSourceErrorV2

    /**
     * Unknown service error occurred.
     *
     * An error occurred that doesn't fit other categories.
     * This provides forward compatibility when the server introduces new
     * error types that the client doesn't recognize yet. The client can
     * continue functioning by treating unknown errors generically until
     * it's updated to handle the new error types explicitly.
     *
     * @property reason Description for logging of the unknown error
     */
    data class UnknownServiceError(val reason: ErrorReason) : RemoteDataSourceErrorV2
}
