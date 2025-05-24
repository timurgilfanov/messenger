package timur.gilfanov.messenger.domain.usecase

sealed class ReceiveChatUpdatesError {
    object ChatNotFound : ReceiveChatUpdatesError()
    object NetworkNotAvailable : ReceiveChatUpdatesError()
    object ServerUnreachable : ReceiveChatUpdatesError()
    object ServerError : ReceiveChatUpdatesError()
    object UnknownError : ReceiveChatUpdatesError()
}
