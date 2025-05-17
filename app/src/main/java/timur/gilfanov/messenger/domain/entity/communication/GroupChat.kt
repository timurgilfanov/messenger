package timur.gilfanov.messenger.domain.entity.communication

import java.util.UUID
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.message.CanNotSendMessageError
import timur.gilfanov.messenger.domain.entity.message.Message

data class GroupChat(
    override val id: UUID,
    override val name: String,
    override val pictureUrl: String?,
    override val messages: List<Message>,
    override val participants: Set<Participant>,
    override val unreadMessagesCount: Int,
    override val lastReadMessageId: UUID? = null,
) : Communication {
    override fun canSendMessage(): ResultWithError<Unit, CanNotSendMessageError> = Success(Unit)
}
