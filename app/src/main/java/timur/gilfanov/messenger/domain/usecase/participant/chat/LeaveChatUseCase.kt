package timur.gilfanov.messenger.domain.usecase.participant.chat

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.participant.chat.LeaveChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.LeaveChatError.LocalError
import timur.gilfanov.messenger.domain.usecase.participant.chat.LeaveChatError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.LeaveChatError.NotParticipant
import timur.gilfanov.messenger.domain.usecase.participant.chat.LeaveChatError.RemoteError
import timur.gilfanov.messenger.domain.usecase.participant.chat.LeaveChatError.RemoteUnreachable
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError as RepositoryError

class LeaveChatUseCase(private val chatId: ChatId, private val repository: Repository) {
    suspend operator fun invoke(): ResultWithError<Unit, LeaveChatError> =
        repository.leaveChat(chatId).let { result ->
            when (result) {
                is Success -> Success(result.data)
                is Failure -> Failure(mapRepositoryError(result.error))
            }
        }

    private fun mapRepositoryError(error: RepositoryError): LeaveChatError = when (error) {
        RepositoryError.NetworkNotAvailable -> NetworkNotAvailable
        RepositoryError.RemoteUnreachable -> RemoteUnreachable
        RepositoryError.RemoteError -> RemoteError
        RepositoryError.LocalError -> LocalError
        RepositoryError.ChatNotFound -> ChatNotFound
        RepositoryError.NotParticipant -> NotParticipant
    }
}
