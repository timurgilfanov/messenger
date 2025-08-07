package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId

class LeaveChatUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatId: ChatId): ResultWithError<Unit, LeaveChatError> =
        repository.leaveChat(chatId).let { result ->
            when (result) {
                is Success -> Success(result.data)
                is Failure -> Failure(result.error)
            }
        }
}
