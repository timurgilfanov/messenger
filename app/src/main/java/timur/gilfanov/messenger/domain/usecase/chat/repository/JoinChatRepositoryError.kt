package timur.gilfanov.messenger.domain.usecase.chat.repository

import kotlin.time.Duration
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

sealed interface JoinChatRepositoryError {
    data object ChatNotFound : JoinChatRepositoryError

    data object InvalidInviteLink : JoinChatRepositoryError

    data object ExpiredInviteLink : JoinChatRepositoryError

    data object ChatClosed : JoinChatRepositoryError

    data object AlreadyJoined : JoinChatRepositoryError

    data object ChatFull : JoinChatRepositoryError

    data object OneToOneChatFull : JoinChatRepositoryError

    data object UserNotFound : JoinChatRepositoryError

    data object UserBlocked : JoinChatRepositoryError

    data class CooldownActive(val remaining: Duration) : JoinChatRepositoryError

    data class LocalOperationFailed(val error: LocalStorageError) : JoinChatRepositoryError

    data class RemoteOperationFailed(val error: RemoteError) : JoinChatRepositoryError
}
