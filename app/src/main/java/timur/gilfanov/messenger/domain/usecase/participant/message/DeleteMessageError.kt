package timur.gilfanov.messenger.domain.usecase.participant.message

import kotlin.time.Duration

sealed class DeleteMessageError {
    data class DeleteWindowExpired(val windowDuration: Duration) : DeleteMessageError()
    object NotAuthorized : DeleteMessageError()
    object MessageAlreadyDelivered : DeleteMessageError()
    data class DeleteForEveryoneWindowExpired(val windowDuration: Duration) : DeleteMessageError()
    object DeleteForEveryoneNotAllowed : DeleteMessageError()
}

sealed class RepositoryDeleteMessageError : DeleteMessageError() {
    object NetworkNotAvailable : RepositoryDeleteMessageError()
    object RemoteUnreachable : RepositoryDeleteMessageError()
    object RemoteError : RepositoryDeleteMessageError()
    object LocalError : RepositoryDeleteMessageError()
    object MessageNotFound : RepositoryDeleteMessageError()
}
