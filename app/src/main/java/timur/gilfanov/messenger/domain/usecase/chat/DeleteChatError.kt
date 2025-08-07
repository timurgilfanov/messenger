package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.chat.ChatId

sealed class DeleteChatError {
    object NotAuthorized : DeleteChatError()
}

sealed class RepositoryDeleteChatError : DeleteChatError() {
    object NetworkNotAvailable : RepositoryDeleteChatError()
    object RemoteUnreachable : RepositoryDeleteChatError()
    object RemoteError : RepositoryDeleteChatError()
    object LocalError : RepositoryDeleteChatError()
    data class ChatNotFound(val chatId: ChatId) : RepositoryDeleteChatError()
}
