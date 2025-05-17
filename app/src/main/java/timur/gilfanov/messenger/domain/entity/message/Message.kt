package timur.gilfanov.messenger.domain.entity.message

import java.util.UUID
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.communication.Communication
import timur.gilfanov.messenger.domain.entity.communication.Participant
import timur.gilfanov.messenger.domain.usecase.ValidationError

interface Message {
    val id: UUID
    val parentId: UUID?
    val sender: Participant
    val recipient: Communication
    val createdAt: Instant
    val sentAt: Instant?
    val deliveredAt: Instant?
    val editedAt: Instant?
    val deliveryStatus: DeliveryStatus?

    fun validate(): ResultWithError<Unit, ValidationError>
}
