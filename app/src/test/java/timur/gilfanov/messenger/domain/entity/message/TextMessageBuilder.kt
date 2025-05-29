package timur.gilfanov.messenger.domain.entity.message

import java.util.UUID
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidatorVersion

fun buildTextMessage(builder: TextMessageBuilder.() -> Unit): TextMessage =
    TextMessageBuilder().apply(builder).build()

class TextMessageBuilder {
    var id: MessageId = MessageId(UUID.randomUUID())
    var parentId: MessageId? = null
    var sender: Participant? = null
    var recipient: ChatId = ChatId(UUID.randomUUID())
    var createdAt: Instant? = null
    var sentAt: Instant? = null
    var deliveredAt: Instant? = null
    var editedAt: Instant? = null
    var text: String = ""
    var deliveryStatus: DeliveryStatus? = null
    var textValidatorVersion: TextValidatorVersion = TextValidatorVersion.Current

    fun build(): TextMessage {
        requireNotNull(sender) { "Sender must be specified" }
        requireNotNull(createdAt) { "CreatedAt must be specified" }

        return TextMessage(
            id = id,
            parentId = parentId,
            sender = sender!!,
            recipient = recipient,
            createdAt = createdAt!!,
            sentAt = sentAt,
            deliveredAt = deliveredAt,
            editedAt = editedAt,
            text = text,
            deliveryStatus = deliveryStatus,
            textValidatorVersion = textValidatorVersion,
        )
    }
}
