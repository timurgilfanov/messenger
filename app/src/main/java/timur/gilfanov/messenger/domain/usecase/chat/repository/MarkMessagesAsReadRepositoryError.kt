package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface MarkMessagesAsReadRepositoryError {
    data object ChatNotFound : MarkMessagesAsReadRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : MarkMessagesAsReadRepositoryError
}
