package timur.gilfanov.messenger.domain.usecase.participant

import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

class ParticipantRepositoryNotImplemented : ParticipantRepository {
    override suspend fun leaveChat(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun flowChatList() = error("Not implemented in delegate")
    override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
        error("Not implemented in delegate")
    override suspend fun editMessage(message: Message) = error("Not implemented in delegate")
    override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
        error("Not implemented in delegate")
    override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun sendMessage(message: Message) = error("Not implemented in delegate")
}
