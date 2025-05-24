package timur.gilfanov.messenger.domain.usecase

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId

class ReceiveChatUpdatesUseCase(private val chatId: ChatId, private val repository: Repository) {
    suspend operator fun invoke(): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> =
        repository.receiveChatUpdates(chatId)
}
