package timur.gilfanov.messenger.domain.entity.message

import java.util.UUID
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.communication.Communication
import timur.gilfanov.messenger.domain.entity.communication.Participant
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidator
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidatorVersion
import timur.gilfanov.messenger.domain.usecase.ValidationError

data class TextMessage(
    override val id: UUID,
    override val parentId: UUID?,
    override val sender: Participant,
    override val recipient: Communication,
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

    companion object {
        const val MAX_TEXT_LENGTH = 2000
    }
}
