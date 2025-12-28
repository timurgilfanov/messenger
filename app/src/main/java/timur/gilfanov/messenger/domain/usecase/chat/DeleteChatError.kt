package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for chat deletion operations.
 *
 * ## Logical Errors
 * - [NotAuthorized] - User not authorized to delete this chat
 * - [ChatNotFound] - Chat does not exist
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface DeleteChatError {
    /**
     * User is not authorized to delete this chat.
     */
    data object NotAuthorized : DeleteChatError

    /**
     * The chat to delete was not found.
     *
     * @property chatId The ID of the chat that was not found
     */
    data class ChatNotFound(val chatId: ChatId) : DeleteChatError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : DeleteChatError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : DeleteChatError
}
