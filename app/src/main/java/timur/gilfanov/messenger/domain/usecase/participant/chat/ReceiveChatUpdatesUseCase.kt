package timur.gilfanov.messenger.domain.usecase.participant.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.ChatRepository

class ReceiveChatUpdatesUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = repository.receiveChatUpdates(chatId)
}
