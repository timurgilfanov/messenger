package timur.gilfanov.messenger.domain.usecase.message

import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.usecase.ValidationError

sealed class EditMessageError {
    object EditWindowExpired : EditMessageError()
    data class MessageIsNotValid(val reason: ValidationError) : EditMessageError()
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : EditMessageError()
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        EditMessageError()
    object CreationTimeChanged : EditMessageError()
    object RecipientChanged : EditMessageError()
    object SenderIdChanged : EditMessageError()
}
