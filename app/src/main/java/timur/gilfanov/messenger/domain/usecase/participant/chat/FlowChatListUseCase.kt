package timur.gilfanov.messenger.domain.usecase.participant.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository

class FlowChatListUseCase(private val repository: ParticipantRepository) {
    suspend operator fun invoke(): Flow<List<Chat>> = repository.flowChatList()
}
