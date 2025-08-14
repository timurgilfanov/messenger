package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.MessageId

sealed class MarkMessagesAsReadError {
    data object ChatNotFound : MarkMessagesAsReadError()
    data object NetworkNotAvailable : MarkMessagesAsReadError()
    data object ServerError : MarkMessagesAsReadError()
    data object ServerUnreachable : MarkMessagesAsReadError()
    data class UnknownError(val throwable: Throwable) : MarkMessagesAsReadError()
}

typealias RepositoryMarkMessagesAsReadError = MarkMessagesAsReadError

class MarkMessagesAsReadUseCase(private val repository: ChatRepository) {
    /**
     * Mark messages as read in a chat up to and including the specified message.
     * This prevents race conditions where undelivered messages could be marked as read.
     * @param chatId The chat where messages should be marked as read
     * @param upToMessageId Mark messages as read up to and including this message ID
     */
    suspend operator fun invoke(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, MarkMessagesAsReadError> =
        repository.markMessagesAsRead(chatId, upToMessageId)
}
