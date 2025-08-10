package timur.gilfanov.messenger.domain.usecase.message

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError

sealed class SendMessageError {
    data class WaitAfterJoining(val duration: Duration) : SendMessageError()
    data class WaitDebounce(val duration: Duration) : SendMessageError()
    data class MessageIsNotValid(val reason: ValidationError) : SendMessageError()
    data class DeliveryStatusAlreadySet(val status: DeliveryStatus) : SendMessageError()
    data class DeliveryStatusUpdateNotValid(val error: DeliveryStatusValidationError) :
        SendMessageError()
}

sealed class RepositorySendMessageError : SendMessageError() {
    object NetworkNotAvailable : RepositorySendMessageError()
    object RemoteUnreachable : RepositorySendMessageError()
    object RemoteError : RepositorySendMessageError()
    object LocalError : RepositorySendMessageError()
}
