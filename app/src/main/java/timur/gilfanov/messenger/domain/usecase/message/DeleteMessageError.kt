package timur.gilfanov.messenger.domain.usecase.message

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.entity.message.MessageId

sealed class DeleteMessageError {
    data class MessageNotFound(val messageId: MessageId) : DeleteMessageError()
    data class DeleteWindowExpired(val windowDuration: Duration) : DeleteMessageError()
    object NotAuthorized : DeleteMessageError()
    object MessageAlreadyDelivered : DeleteMessageError()
    data class DeleteForEveryoneWindowExpired(val windowDuration: Duration) : DeleteMessageError()
    object DeleteForEveryoneNotAllowed : DeleteMessageError()
    object NetworkNotAvailable : DeleteMessageError()
    object RemoteUnreachable : DeleteMessageError()
    object RemoteError : DeleteMessageError()
    object LocalError : DeleteMessageError()
}
