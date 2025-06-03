package timur.gilfanov.messenger.domain.usecase.participant.message

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError

sealed class CreateMessageError {
    data class WaitAfterJoining(val duration: Duration) : CreateMessageError()
    data class WaitDebounce(val duration: Duration) : CreateMessageError()
    data class MessageIsNotValid(val reason: ValidationError) : CreateMessageError()
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : CreateMessageError()
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        CreateMessageError()
}
