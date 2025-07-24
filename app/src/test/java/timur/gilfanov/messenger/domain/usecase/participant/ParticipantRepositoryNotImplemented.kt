package timur.gilfanov.messenger.domain.usecase.participant

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

class ParticipantRepositoryNotImplemented : ParticipantRepository {
    override suspend fun leaveChat(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
        error("Not implemented in delegate")
    override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
        error("Not implemented in delegate")
    override suspend fun editMessage(message: Message) = error("Not implemented in delegate")
    override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
        error("Not implemented in delegate")
    override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun sendMessage(message: Message) = error("Not implemented in delegate")
    override fun isChatListUpdating() = error("Not implemented in delegate")
}
