package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for chat leave operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 * - [NotParticipant] - User is not a participant of this chat
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LeaveChatError {
    /**
     * The chat to leave was not found.
     */
    data object ChatNotFound : LeaveChatError

    /**
     * User is not a participant of this chat.
     */
    data object NotParticipant : LeaveChatError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : LeaveChatError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : LeaveChatError
}
