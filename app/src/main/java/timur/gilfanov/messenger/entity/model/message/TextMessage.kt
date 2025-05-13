package timur.gilfanov.messenger.entity.model.message

import java.util.UUID
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.entity.delegate.DeliveryStatusDelegate
import timur.gilfanov.messenger.entity.delegate.TextDelegate
import timur.gilfanov.messenger.entity.model.user.User

class TextMessage(
    override val id: UUID,
    override val sender: User,
    override val recipient: User,
    override val sentAt: Instant,
    deliveryStatusInitialValue: DeliveryStatus,
    textInitialValue: String,
) : Message {

    override var deliveryStatus: DeliveryStatus by DeliveryStatusDelegate(
        deliveryStatusInitialValue,
    )

    var text: String by TextDelegate(textInitialValue, MAX_TEXT_LENGTH)

    companion object {
        const val MAX_TEXT_LENGTH = 2000
    }
}
