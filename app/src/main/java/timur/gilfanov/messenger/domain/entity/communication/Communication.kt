package timur.gilfanov.messenger.domain.entity.communication

import java.util.UUID
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.CanNotSendMessageError
import timur.gilfanov.messenger.domain.entity.message.Message

interface Communication {
    val id: UUID
    val name: String
    val pictureUrl: String?
    val messages: List<Message>
    val participants: Set<Participant>
    val unreadMessagesCount: Int
    val lastReadMessageId: UUID?

    fun canSendMessage(): ResultWithError<Unit, CanNotSendMessageError>
}
