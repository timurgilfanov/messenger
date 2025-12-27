package timur.gilfanov.messenger.domain.usecase.message.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface DeleteMessageRepositoryError {
    data object MessageNotFound : DeleteMessageRepositoryError

    data class LocalOperationFailed(val error: LocalStorageError) : DeleteMessageRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : DeleteMessageRepositoryError
}
