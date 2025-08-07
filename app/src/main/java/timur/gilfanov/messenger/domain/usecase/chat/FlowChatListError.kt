package timur.gilfanov.messenger.domain.usecase.chat

sealed class FlowChatListError {
    object NetworkNotAvailable : FlowChatListError()
    object RemoteUnreachable : FlowChatListError()
    object RemoteError : FlowChatListError()
    object LocalError : FlowChatListError()
}
