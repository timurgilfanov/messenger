package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for chat deletion repository operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface DeleteChatRepositoryError {
    /**
     * The chat to delete was not found.
     *
     * @property chatId The ID of the chat that was not found
     */
    data class ChatNotFound(val chatId: ChatId) : DeleteChatRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : DeleteChatRepositoryError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : DeleteChatRepositoryError
}
