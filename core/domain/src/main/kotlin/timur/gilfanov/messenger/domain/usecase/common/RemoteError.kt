package timur.gilfanov.messenger.domain.usecase.common

import kotlin.time.Duration

/**
 * Subset of [RemoteError] that is valid for unauthenticated operations (login, signup).
 *
 * Excludes [RemoteError.Unauthenticated] and [RemoteError.InsufficientPermissions], which are
 * logically impossible for operations that do not require an existing session.
 */
sealed interface UnauthRemoteError

/**
 * Common error taxonomy for repository operations involving remote data.
 *
 * Introduced to remove duplication across multiple use cases that share similar error modes.
 *
 * ## Authentication Errors (Who are you?)
 * - [Unauthenticated] - Caller is not authenticated or session expired
 *
 * ## Authorization Errors (What can you do?)
 * - [InsufficientPermissions] - Caller lacks permission for the operation
 *
 * ## Operation Outcome Errors
 * - [Failed] - Operation definitely failed with a known error
 * - [UnknownStatus] - Operation outcome is unknown
 */
sealed interface RemoteError {
    /** Caller is unauthenticated or the session expired. */
    data object Unauthenticated : RemoteError

    /** Caller lacks permission to perform the operation for the current identity. */
    data object InsufficientPermissions : RemoteError

    /**
     * Operation definitely failed. Each subtype captures a concrete failure mode reported by lower
     * layers, such as network connectivity, service throttling, or unexpected infrastructure
     * issues.
     */
    sealed interface Failed :
        RemoteError,
        UnauthRemoteError {
        /** No network connectivity at the moment of the operation attempt. */
        data object NetworkNotAvailable : Failed

        /** Remote service rejected the request because it is unavailable or overloaded. */
        data object ServiceDown : Failed

        /** Remote service returned a cooldown/too-many-requests window; includes remaining wait. */
        data class Cooldown(val remaining: Duration) : Failed

        /**
         * An unclassified infrastructure error occurred.
         *
         * @property cause Reason for the error
         */
        data class UnknownServiceError(val cause: ErrorReason) : Failed
    }

    /**
     * The operation outcome is unknown (e.g., request timed out after being sent). Callers may need
     * to query status or re-attempt once the underlying condition clears.
     */
    sealed interface UnknownStatus :
        RemoteError,
        UnauthRemoteError {
        /** Request timed out before the service confirmed success or failure. */
        data object ServiceTimeout : UnknownStatus
    }
}
