package timur.gilfanov.messenger.domain.usecase.participant.chat

import kotlin.time.Duration

sealed class JoinChatError

sealed class RepositoryJoinChatError : JoinChatError() {
    object NetworkNotAvailable : RepositoryJoinChatError()
    object RemoteUnreachable : RepositoryJoinChatError()
    object RemoteError : RepositoryJoinChatError()
    object LocalError : RepositoryJoinChatError()
    object ChatNotFound : RepositoryJoinChatError()
    object InvalidInviteLink : RepositoryJoinChatError()
    object ExpiredInviteLink : RepositoryJoinChatError()
    object ChatClosed : RepositoryJoinChatError()
    object AlreadyJoined : RepositoryJoinChatError()
    object ChatFull : RepositoryJoinChatError()
    object OneToOneChatFull : RepositoryJoinChatError()
    object UserNotFound : RepositoryJoinChatError()
    object UserBlocked : RepositoryJoinChatError()
    data class CooldownActive(val remaining: Duration) : RepositoryJoinChatError()
}
