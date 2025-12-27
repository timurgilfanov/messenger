package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface ReceiveChatUpdatesRepositoryError {
    data object ChatNotFound : ReceiveChatUpdatesRepositoryError

    data class LocalOperationFailed(val error: LocalStorageError) :
        ReceiveChatUpdatesRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : ReceiveChatUpdatesRepositoryError
}
