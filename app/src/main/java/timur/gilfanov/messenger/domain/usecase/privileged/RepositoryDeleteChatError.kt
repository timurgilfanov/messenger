package timur.gilfanov.messenger.domain.usecase.privileged

import timur.gilfanov.messenger.domain.entity.chat.ChatId

sealed class RepositoryDeleteChatError {
    object NetworkNotAvailable : RepositoryDeleteChatError()
    object RemoteUnreachable : RepositoryDeleteChatError()
    object RemoteError : RepositoryDeleteChatError()
    object LocalError : RepositoryDeleteChatError()
    data class ChatNotFound(val chatId: ChatId) : RepositoryDeleteChatError()
}
