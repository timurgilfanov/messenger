package timur.gilfanov.messenger.domain.usecase.message

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for message deletion operations.
 *
 * ## Logical Errors
 * - [DeleteWindowExpired] - Delete window has expired
 * - [NotAuthorized] - User not authorized to delete this message
 * - [MessageAlreadyDelivered] - Message was already delivered
 * - [DeleteForEveryoneWindowExpired] - Delete for everyone window has expired
 * - [DeleteForEveryoneNotAllowed] - Delete for everyone not allowed
 * - [MessageNotFound] - Message does not exist
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface DeleteMessageError {
    /**
     * Delete window has expired.
     *
     * @property windowDuration The delete window duration
     */
    data class DeleteWindowExpired(val windowDuration: Duration) : DeleteMessageError

    /**
     * User is not authorized to delete this message.
     */
    data object NotAuthorized : DeleteMessageError

    /**
     * Message was already delivered and cannot be deleted.
     */
    data object MessageAlreadyDelivered : DeleteMessageError

    /**
     * Delete for everyone window has expired.
     *
     * @property windowDuration The delete for everyone window duration
     */
    data class DeleteForEveryoneWindowExpired(val windowDuration: Duration) : DeleteMessageError

    /**
     * Delete for everyone is not allowed for this message.
     */
    data object DeleteForEveryoneNotAllowed : DeleteMessageError

    /**
     * The message to delete was not found.
     */
    data object MessageNotFound : DeleteMessageError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : DeleteMessageError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : DeleteMessageError
}
