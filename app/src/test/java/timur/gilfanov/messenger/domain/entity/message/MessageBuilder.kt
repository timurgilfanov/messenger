package timur.gilfanov.messenger.domain.entity.message

import java.util.UUID
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant

fun buildMessage(builder: MessageBuilder.() -> Unit): Message =
    MessageBuilder().apply(builder).build()

class MessageBuilder {
    var id: MessageId = MessageId(UUID.randomUUID())
    var parentId: MessageId? = null
    var sender: Participant? = null
    var recipient: ChatId = ChatId(UUID.randomUUID())
    var createdAt: Instant? = null
    var sentAt: Instant? = null
    var deliveredAt: Instant? = null
    var editedAt: Instant? = null
    var deliveryStatus: DeliveryStatus? = null
    var validationResult: ResultWithError<Unit, ValidationError> = Success(Unit)

    fun build(): Message {
        requireNotNull(sender) { "Sender must be specified" }
        requireNotNull(createdAt) { "CreatedAt must be specified" }
        return object : Message {
            override val id: MessageId = this@MessageBuilder.id
            override val parentId: MessageId? = this@MessageBuilder.parentId
            override val sender: Participant = this@MessageBuilder.sender!!
            override val recipient: ChatId = this@MessageBuilder.recipient
            override val createdAt: Instant = this@MessageBuilder.createdAt!!
            override val sentAt: Instant? = this@MessageBuilder.sentAt
            override val deliveredAt: Instant? = this@MessageBuilder.deliveredAt
            override val editedAt: Instant? = this@MessageBuilder.editedAt
            override val deliveryStatus: DeliveryStatus? = this@MessageBuilder.deliveryStatus
            override fun validate(): ResultWithError<Unit, ValidationError> = validationResult
        }
    }
}
