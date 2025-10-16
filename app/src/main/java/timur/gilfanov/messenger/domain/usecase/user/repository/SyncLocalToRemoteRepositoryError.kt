package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlin.time.Duration

/**
 * Error taxonomy for the "sync local settings to remote" use case.
 *
 * This sealed hierarchy keeps authentication/authorization problems separate from transport
 * failures and “status unknown” responses, helping callers decide how to handle the error.
 */
sealed interface SyncLocalToRemoteRepositoryError {
    /** Caller is unauthenticated or the session expired. */
    data object Unauthenticated : SyncLocalToRemoteRepositoryError

    /** Caller lacks permission to update remote settings for the current identity. */
    data object InsufficientPermissions : SyncLocalToRemoteRepositoryError

    /**
     * Sync definitely failed. Each subtype captures a concrete failure mode reported by lower
     * layers, such as network connectivity, service throttling, or unexpected infrastructure
     * issues.
     */
    sealed interface Failed : SyncLocalToRemoteRepositoryError {
        /** No network connectivity at the moment of the sync attempt. */
        data object NetworkNotAvailable : Failed

        /** Remote service rejected the request because it is unavailable or overloaded. */
        data object ServiceDown : Failed

        /** Remote service returned a cooldown/too-many-requests window; includes remaining wait. */
        data class Cooldown(val remaining: Duration) : Failed

        /** An unclassified infrastructure error occurred. */
        data object UnknownError : Failed
    }

    /**
     * The sync outcome is unknown (e.g., request timed out after being sent). Callers may need to
     * query status or re-attempt once the underlying condition clears.
     */
    sealed interface StatusUnknown : SyncLocalToRemoteRepositoryError {
        /** Request timed out before the service confirmed success or failure. */
        data object ServiceTimeout : StatusUnknown
    }
}
