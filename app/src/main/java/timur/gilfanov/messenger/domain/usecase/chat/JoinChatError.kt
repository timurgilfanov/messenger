package timur.gilfanov.messenger.domain.usecase.chat

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for chat join operations.
 *
 * ## Logical Errors
 * - [ChatNotFound] - Chat does not exist
 * - [InvalidInviteLink] - Invite link is invalid
 * - [ExpiredInviteLink] - Invite link has expired
 * - [ChatClosed] - Chat is closed for new members
 * - [AlreadyJoined] - User is already a member
 * - [ChatFull] - Chat has reached member limit
 * - [OneToOneChatFull] - One-to-one chat already has two members
 * - [UserNotFound] - User to join does not exist
 * - [UserBlocked] - User is blocked from joining
 * - [CooldownActive] - Must wait before joining
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface JoinChatError {
    /**
     * The chat to join was not found.
     */
    data object ChatNotFound : JoinChatError

    /**
     * The invite link is invalid.
     */
    data object InvalidInviteLink : JoinChatError

    /**
     * The invite link has expired.
     */
    data object ExpiredInviteLink : JoinChatError

    /**
     * The chat is closed for new members.
     */
    data object ChatClosed : JoinChatError

    /**
     * User is already a member of this chat.
     */
    data object AlreadyJoined : JoinChatError

    /**
     * Chat has reached its member limit.
     */
    data object ChatFull : JoinChatError

    /**
     * One-to-one chat already has two members.
     */
    data object OneToOneChatFull : JoinChatError

    /**
     * The user to join does not exist.
     */
    data object UserNotFound : JoinChatError

    /**
     * User is blocked from joining this chat.
     */
    data object UserBlocked : JoinChatError

    /**
     * Must wait before joining.
     *
     * @property remaining Time remaining before user can join
     */
    data class CooldownActive(val remaining: Duration) : JoinChatError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : JoinChatError

    /**
     * Remote operation failed.
     *
     * @property error The underlying [RemoteError] instance
     */
    data class RemoteOperationFailed(val error: RemoteError) : JoinChatError
}
