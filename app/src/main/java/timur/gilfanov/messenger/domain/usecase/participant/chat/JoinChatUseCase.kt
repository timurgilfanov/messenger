package timur.gilfanov.messenger.domain.usecase.participant.chat

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.AlreadyInChat
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ChatClosed
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ChatFull
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.CooldownActive
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ExpiredInviteLink
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.InvalidInviteLink
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.LocalError
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.OneToOneChatFull
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.RemoteError
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.RemoteUnreachable
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.UserBlocked
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.UserNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError as RepositoryError

class JoinChatUseCase(
    private val chatId: ChatId,
    private val inviteLink: String? = null,
    private val repository: ParticipantRepository,
) {
    suspend operator fun invoke(): ResultWithError<Chat, JoinChatError> =
        repository.joinChat(chatId, inviteLink).let { result ->
            when (result) {
                is Success -> Success(result.data)
                is Failure -> Failure(mapRepositoryError(result.error))
            }
        }

    @Suppress("CyclomaticComplexMethod")
    private fun mapRepositoryError(error: RepositoryError): JoinChatError = when (error) {
        RepositoryError.NetworkNotAvailable -> NetworkNotAvailable
        RepositoryError.RemoteUnreachable -> RemoteUnreachable
        RepositoryError.RemoteError -> RemoteError
        RepositoryError.LocalError -> LocalError
        RepositoryError.ChatNotFound -> ChatNotFound
        RepositoryError.UserNotFound -> UserNotFound
        RepositoryError.AlreadyJoined -> AlreadyInChat
        RepositoryError.ChatClosed -> ChatClosed
        RepositoryError.ChatFull -> ChatFull
        RepositoryError.OneToOneChatFull -> OneToOneChatFull
        RepositoryError.UserBlocked -> UserBlocked
        is RepositoryError.CooldownActive -> CooldownActive(error.remaining)
        RepositoryError.InvalidInviteLink -> InvalidInviteLink
        RepositoryError.ExpiredInviteLink -> ExpiredInviteLink
    }
}
