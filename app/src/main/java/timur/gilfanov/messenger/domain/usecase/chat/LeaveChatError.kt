package timur.gilfanov.messenger.domain.usecase.chat

sealed class LeaveChatError
sealed class RepositoryLeaveChatError : LeaveChatError() {
    object NetworkNotAvailable : RepositoryLeaveChatError()
    object RemoteUnreachable : RepositoryLeaveChatError()
    object RemoteError : RepositoryLeaveChatError()
    object LocalError : RepositoryLeaveChatError()
    object ChatNotFound : RepositoryLeaveChatError()
    object NotParticipant : RepositoryLeaveChatError()
}
