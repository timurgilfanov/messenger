package timur.gilfanov.messenger.domain.usecase.chat

import kotlin.time.Duration

sealed class JoinChatError

object NetworkNotAvailable : JoinChatError()
object RemoteUnreachable : JoinChatError()
object RemoteError : JoinChatError()
object LocalError : JoinChatError()
object ChatNotFound : JoinChatError()
object UserNotFound : JoinChatError()
object AlreadyInChat : JoinChatError()
object ChatClosed : JoinChatError()
object ChatFull : JoinChatError()
object OneToOneChatFull : JoinChatError()
object InvalidInviteLink : JoinChatError()
object ExpiredInviteLink : JoinChatError()
object UserBlocked : JoinChatError()
data class CooldownActive(val remaining: Duration) : JoinChatError()
