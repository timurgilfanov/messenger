package timur.gilfanov.messenger.domain.usecase.chat.repository

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for join chat repository operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 * - [InvalidInviteLink] - The invite link is malformed or invalid
 * - [ExpiredInviteLink] - The invite link has expired
 * - [ChatClosed] - The chat is closed for new members
 * - [AlreadyJoined] - User is already a member of this chat
 * - [ChatFull] - The chat has reached its member limit
 * - [OneToOneChatFull] - Cannot join a one-to-one chat that already has two participants
 * - [UserNotFound] - The user to join was not found
 * - [UserBlocked] - The user is blocked from joining this chat
 * - [CooldownActive] - User must wait before joining again
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface JoinChatRepositoryError {
    /**
     * The chat to join was not found.
     */
    data object ChatNotFound : JoinChatRepositoryError

    /**
     * The invite link is malformed or invalid.
     */
    data object InvalidInviteLink : JoinChatRepositoryError

    /**
     * The invite link has expired.
     */
    data object ExpiredInviteLink : JoinChatRepositoryError

    /**
     * The chat is closed for new members.
     */
    data object ChatClosed : JoinChatRepositoryError

    /**
     * User is already a member of this chat.
     */
    data object AlreadyJoined : JoinChatRepositoryError

    /**
     * The chat has reached its member limit.
     */
    data object ChatFull : JoinChatRepositoryError

    /**
     * Cannot join a one-to-one chat that already has two participants.
     */
    data object OneToOneChatFull : JoinChatRepositoryError

    /**
     * The user to join was not found.
     */
    data object UserNotFound : JoinChatRepositoryError

    /**
     * The user is blocked from joining this chat.
     */
    data object UserBlocked : JoinChatRepositoryError

    /**
     * User must wait before joining again.
     *
     * @property remaining The remaining cooldown duration
     */
    data class CooldownActive(val remaining: Duration) : JoinChatRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : JoinChatRepositoryError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : JoinChatRepositoryError
}
