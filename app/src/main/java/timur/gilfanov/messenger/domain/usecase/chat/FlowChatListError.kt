package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Errors for chat list flow operations.
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface FlowChatListError {
    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : FlowChatListError
}
