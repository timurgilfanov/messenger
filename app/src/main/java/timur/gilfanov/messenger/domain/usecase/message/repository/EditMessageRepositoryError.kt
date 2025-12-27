package timur.gilfanov.messenger.domain.usecase.message.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface EditMessageRepositoryError {
    data class LocalOperationFailed(val error: LocalStorageError) : EditMessageRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : EditMessageRepositoryError
}
