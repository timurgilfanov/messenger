package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryJoinChatError as RepositoryError

class JoinChatUseCase(
    private val chatId: ChatId,
    private val inviteLink: String? = null,
    private val repository: Repository,
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
