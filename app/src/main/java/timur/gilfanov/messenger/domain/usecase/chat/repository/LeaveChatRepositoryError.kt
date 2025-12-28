package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for leave chat repository operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 * - [NotParticipant] - User is not a participant of this chat
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LeaveChatRepositoryError {
    /**
     * The chat to leave was not found.
     */
    data object ChatNotFound : LeaveChatRepositoryError

    /**
     * User is not a participant of this chat.
     */
    data object NotParticipant : LeaveChatRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : LeaveChatRepositoryError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : LeaveChatRepositoryError
}
