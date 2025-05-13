package timur.gilfanov.messenger.entity.model.message

import java.util.UUID
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.entity.model.user.User

interface Message {
    val id: UUID
    val sender: User
    val recipient: User
    val sentAt: Instant
    var deliveryStatus: DeliveryStatus
}
