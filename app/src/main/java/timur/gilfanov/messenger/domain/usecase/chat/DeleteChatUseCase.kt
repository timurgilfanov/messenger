package timur.gilfanov.messenger.domain.usecase.chat

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule.OnlyAdminCanDelete
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryDeleteChatError as RepositoryError

class DeleteChatUseCase(
    private val chat: Chat,
    private val currentUser: Participant,
    private val repository: Repository,
) {
    suspend operator fun invoke(): ResultWithError<Unit, DeleteChatError> {
        checkRules(
            hasAdminPrivileges = currentUser.isAdmin,
        ).let { result ->
            if (result is Failure) return result
        }

        return repository.deleteChat(chat.id).let { result ->
            when (result) {
                is Success -> Success(Unit)
                is Failure -> Failure(
                    when (val e = result.error) {
                        RepositoryError.NetworkNotAvailable -> DeleteChatError.NetworkNotAvailable
                        RepositoryError.RemoteUnreachable -> DeleteChatError.RemoteUnreachable
                        RepositoryError.RemoteError -> DeleteChatError.RemoteError
                        RepositoryError.LocalError -> DeleteChatError.LocalError
                        is RepositoryError.ChatNotFound -> DeleteChatError.ChatNotFound(e.chatId)
                    },
                )
            }
        }
    }

    private fun checkRules(hasAdminPrivileges: Boolean): ResultWithError<Unit, DeleteChatError> {
        val deleteChatRules = chat.rules.filterIsInstance<DeleteChatRule>()

        for (rule in deleteChatRules) {
            val ruleResult = when (rule) {
                OnlyAdminCanDelete -> checkAdminPrivileges(hasAdminPrivileges)
            }

            if (ruleResult is Failure) {
                return ruleResult
            }
        }

        return Success(Unit)
    }

    private fun checkAdminPrivileges(
        hasAdminPrivileges: Boolean,
    ): ResultWithError<Unit, DeleteChatError> = if (hasAdminPrivileges) {
        Success(Unit)
    } else {
        Failure(DeleteChatError.NotAuthorized)
    }
}
