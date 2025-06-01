package timur.gilfanov.messenger.domain.usecase.priveleged

import timur.gilfanov.messenger.domain.entity.chat.ChatId

sealed class DeleteChatError {
    object NotAuthorized : DeleteChatError()
    object NetworkNotAvailable : DeleteChatError()
    object RemoteUnreachable : DeleteChatError()
    object RemoteError : DeleteChatError()
    object LocalError : DeleteChatError()
    data class ChatNotFound(val chatId: ChatId) : DeleteChatError()
}
