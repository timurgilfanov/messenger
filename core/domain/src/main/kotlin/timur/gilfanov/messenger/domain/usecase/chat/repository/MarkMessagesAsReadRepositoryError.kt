package timur.gilfanov.messenger.domain.usecase.chat.repository

import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for mark messages as read repository operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 *
 * ## Data Source Errors
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface MarkMessagesAsReadRepositoryError {
    /**
     * The chat was not found.
     */
    data object ChatNotFound : MarkMessagesAsReadRepositoryError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : MarkMessagesAsReadRepositoryError
}
