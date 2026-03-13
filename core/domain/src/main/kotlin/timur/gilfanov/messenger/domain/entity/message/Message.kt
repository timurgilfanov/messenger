package timur.gilfanov.messenger.domain.entity.message

import java.util.UUID
import kotlin.time.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant

@JvmInline
value class MessageId(val id: UUID)

interface Message {
    val id: MessageId
    val parentId: MessageId?
    val sender: Participant
    val recipient: ChatId
    val createdAt: Instant
    val sentAt: Instant?
    val deliveredAt: Instant?
    val editedAt: Instant?
    val deliveryStatus: DeliveryStatus?

    fun validate(): ResultWithError<Unit, ValidationError>
}
