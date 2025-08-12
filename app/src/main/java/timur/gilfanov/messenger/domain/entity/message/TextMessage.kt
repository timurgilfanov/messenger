package timur.gilfanov.messenger.domain.entity.message

import kotlin.time.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidator
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidatorVersion

data class TextMessage(
    override val id: MessageId,
    override val parentId: MessageId?,
    override val sender: Participant,
    override val recipient: ChatId,
    override val createdAt: Instant,
    override val sentAt: Instant? = null,
    override val deliveredAt: Instant? = null,
    override val editedAt: Instant? = null,
    override val deliveryStatus: DeliveryStatus? = null,
    val text: String,
    val textValidatorVersion: TextValidatorVersion = TextValidatorVersion.Current,
) : Message {

    override fun validate(): ResultWithError<Unit, ValidationError> {
        val validator = when (textValidatorVersion) {
            TextValidatorVersion.Current -> TextValidator(MAX_TEXT_LENGTH)
        }
        return validator.validate(text)
    }

    override fun toString(): String = "from $sender to $recipient: $text"

    companion object {
        const val MAX_TEXT_LENGTH = 2000
    }
}
