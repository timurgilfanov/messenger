package timur.gilfanov.messenger.domain.usecase.participant.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

class FlowChatListUseCase(private val repository: ParticipantRepository) {
    suspend operator fun invoke(): Flow<ResultWithError<List<ChatPreview>, FlowChatListError>> =
        repository.flowChatList()
}
