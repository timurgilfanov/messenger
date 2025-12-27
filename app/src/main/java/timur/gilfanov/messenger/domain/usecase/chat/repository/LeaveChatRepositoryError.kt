package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface LeaveChatRepositoryError {
    data object ChatNotFound : LeaveChatRepositoryError

    data object NotParticipant : LeaveChatRepositoryError

    data class LocalOperationFailed(val error: LocalStorageError) : LeaveChatRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : LeaveChatRepositoryError
}
