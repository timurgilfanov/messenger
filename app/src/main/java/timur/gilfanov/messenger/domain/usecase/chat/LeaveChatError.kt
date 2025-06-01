package timur.gilfanov.messenger.domain.usecase.chat

sealed class LeaveChatError {
    object NetworkNotAvailable : LeaveChatError()
    object RemoteUnreachable : LeaveChatError()
    object RemoteError : LeaveChatError()
    object LocalError : LeaveChatError()
    object ChatNotFound : LeaveChatError()
    object NotParticipant : LeaveChatError()
}
