package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for receiving chat updates operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface ReceiveChatUpdatesError {
    /**
     * The chat was not found.
     */
    data object ChatNotFound : ReceiveChatUpdatesError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : ReceiveChatUpdatesError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : ReceiveChatUpdatesError
}
