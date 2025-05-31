package timur.gilfanov.messenger.domain.usecase.message

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.usecase.ValidationError

sealed class CreateMessageError {
    data class WaitAfterJoining(val duration: Duration) : CreateMessageError()
    data class WaitDebounce(val duration: Duration) : CreateMessageError()
    data class MessageIsNotValid(val reason: ValidationError) : CreateMessageError()
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : CreateMessageError()
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        CreateMessageError()
}
