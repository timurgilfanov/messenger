package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface CreateChatRepositoryError {
    data object DuplicateChatId : CreateChatRepositoryError

    data class LocalOperationFailed(val error: LocalStorageError) : CreateChatRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : CreateChatRepositoryError
}
