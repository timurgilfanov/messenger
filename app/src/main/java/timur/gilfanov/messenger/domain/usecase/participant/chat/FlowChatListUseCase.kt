package timur.gilfanov.messenger.domain.usecase.participant.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.usecase.ChatRepository

class FlowChatListUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): Flow<ResultWithError<List<ChatPreview>, FlowChatListError>> =
        repository.flowChatList()
}
