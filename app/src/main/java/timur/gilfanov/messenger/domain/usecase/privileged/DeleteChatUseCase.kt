package timur.gilfanov.messenger.domain.usecase.privileged

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule.OnlyAdminCanDelete
import timur.gilfanov.messenger.domain.entity.chat.Participant

class DeleteChatUseCase(private val repository: PrivilegedRepository) {
    suspend operator fun invoke(
        chat: Chat,
        currentUser: Participant,
    ): ResultWithError<Unit, DeleteChatError> {
        checkRules(chat, hasAdminPrivileges = currentUser.isAdmin).let { result ->
            if (result is Failure) return result
        }

        return repository.deleteChat(chat.id).let { result ->
            when (result) {
                is Success -> Success(Unit)
                is Failure -> Failure(result.error)
            }
        }
    }

    private fun checkRules(
        chat: Chat,
        hasAdminPrivileges: Boolean,
    ): ResultWithError<Unit, DeleteChatError> {
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
