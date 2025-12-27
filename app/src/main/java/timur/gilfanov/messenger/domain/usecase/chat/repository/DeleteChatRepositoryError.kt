package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface DeleteChatRepositoryError {
    data class ChatNotFound(val chatId: ChatId) : DeleteChatRepositoryError

    data class LocalOperationFailed(val error: LocalStorageError) : DeleteChatRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : DeleteChatRepositoryError
}
