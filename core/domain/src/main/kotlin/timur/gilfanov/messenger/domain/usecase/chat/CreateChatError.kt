package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError
import timur.gilfanov.messenger.domain.usecase.chat.repository.CreateChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for chat creation operations.
 *
 * ## Logical Errors
 * - [ChatIsNotValid] - Chat validation failed
 * - [DuplicateChatId] - Chat with this ID already exists
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface CreateChatError {
    /**
     * Chat validation failed.
     *
     * @property error The validation error details
     */
    data class ChatIsNotValid(val error: ChatValidationError) : CreateChatError

    /**
     * A chat with this ID already exists.
     */
    data object DuplicateChatId : CreateChatError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : CreateChatError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : CreateChatError
}

internal fun CreateChatRepositoryError.toUseCaseError(): CreateChatError = when (this) {
    is CreateChatRepositoryError.DuplicateChatId -> CreateChatError.DuplicateChatId
    is CreateChatRepositoryError.LocalOperationFailed ->
        CreateChatError.LocalOperationFailed(error)
    is CreateChatRepositoryError.RemoteOperationFailed ->
        CreateChatError.RemoteOperationFailed(error)
}
