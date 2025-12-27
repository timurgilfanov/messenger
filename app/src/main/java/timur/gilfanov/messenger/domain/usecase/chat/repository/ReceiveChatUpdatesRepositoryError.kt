package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for receive chat updates repository operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface ReceiveChatUpdatesRepositoryError {
    /**
     * The chat was not found.
     */
    data object ChatNotFound : ReceiveChatUpdatesRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) :
        ReceiveChatUpdatesRepositoryError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : ReceiveChatUpdatesRepositoryError
}
