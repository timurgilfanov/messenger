package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Errors for flow chat list repository operations.
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface FlowChatListRepositoryError {
    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : FlowChatListRepositoryError
}
