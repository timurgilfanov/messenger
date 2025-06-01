package timur.gilfanov.messenger.domain.usecase.participant.message

import timur.gilfanov.messenger.domain.entity.message.MessageId

sealed class RepositoryDeleteMessageError {
    object NetworkNotAvailable : RepositoryDeleteMessageError()
    object RemoteUnreachable : RepositoryDeleteMessageError()
    object RemoteError : RepositoryDeleteMessageError()
    object LocalError : RepositoryDeleteMessageError()
    data class MessageNotFound(val messageId: MessageId) : RepositoryDeleteMessageError()
}
