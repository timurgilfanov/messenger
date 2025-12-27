package timur.gilfanov.messenger.domain.usecase.message.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface SendMessageRepositoryError {
    data class LocalOperationFailed(val error: LocalStorageError) : SendMessageRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : SendMessageRepositoryError
}
