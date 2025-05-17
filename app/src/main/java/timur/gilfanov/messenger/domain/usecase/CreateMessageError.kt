package timur.gilfanov.messenger.domain.usecase

import timur.gilfanov.messenger.domain.entity.message.CanNotSendMessageError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError

sealed class CreateMessageError {
    data class Unauthorized(val reason: CanNotSendMessageError) : CreateMessageError()
    data class MessageIsNotValid(val reason: ValidationError) : CreateMessageError()
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : CreateMessageError()
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        CreateMessageError()
}
