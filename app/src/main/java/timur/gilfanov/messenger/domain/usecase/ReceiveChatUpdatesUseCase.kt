package timur.gilfanov.messenger.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId

class ReceiveChatUpdatesUseCase(private val chatId: ChatId, private val repository: Repository) {
    operator fun invoke(): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = flow {
        repository.receiveChatUpdates(chatId).collect { result -> emit(result) }
    }
}
