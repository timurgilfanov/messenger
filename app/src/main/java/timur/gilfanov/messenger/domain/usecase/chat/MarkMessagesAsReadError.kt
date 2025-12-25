package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface MarkMessagesAsReadError {
    data object ChatNotFound : MarkMessagesAsReadError

    data class RemoteOperationFailed(val error: RemoteError) : MarkMessagesAsReadError
}
