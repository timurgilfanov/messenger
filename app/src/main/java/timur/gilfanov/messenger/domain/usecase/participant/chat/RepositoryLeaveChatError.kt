package timur.gilfanov.messenger.domain.usecase.participant.chat

sealed class RepositoryLeaveChatError {
    object NetworkNotAvailable : RepositoryLeaveChatError()
    object RemoteUnreachable : RepositoryLeaveChatError()
    object RemoteError : RepositoryLeaveChatError()
    object LocalError : RepositoryLeaveChatError()
    object ChatNotFound : RepositoryLeaveChatError()
    object NotParticipant : RepositoryLeaveChatError()
}
