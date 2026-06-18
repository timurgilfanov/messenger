package timur.gilfanov.messenger.domain.usecase.profile

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors that can occur during profile observation operations.
 *
 * Represents failures retrieving profile-related identity or repository data for profile access.
 */
sealed interface ObserveProfileError {
    /**
     * Failed to retrieve current user identity for profile observation.
     *
     * This error occurs when the identity needed for profile observation cannot be retrieved.
     */
    data object Unauthorized : ObserveProfileError

    /**
     * Local storage operation failed while reading or caching profile data.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : ObserveProfileError

    /**
     * Remote operation failed while refreshing profile data.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : ObserveProfileError
}
