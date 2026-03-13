package timur.gilfanov.messenger.domain.usecase.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

class FlowChatListUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(): Flow<ResultWithError<List<ChatPreview>, FlowChatListError>> =
        repository.flowChatList()
}
