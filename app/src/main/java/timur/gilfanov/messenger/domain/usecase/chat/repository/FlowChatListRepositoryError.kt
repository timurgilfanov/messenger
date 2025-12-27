package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

sealed interface FlowChatListRepositoryError {
    data class LocalOperationFailed(val error: LocalStorageError) : FlowChatListRepositoryError
}
