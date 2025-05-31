package timur.gilfanov.messenger.domain.usecase.message

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.AdminCanDeleteAny
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.DeleteForEveryoneWindow
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.DeleteWindow
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.ModeratorCanDeleteAny
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.NoDeleteAfterDelivered
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.SenderCanDeleteOwn
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode.FOR_SENDER_ONLY
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.DeleteForEveryoneWindowExpired
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.DeleteWindowExpired
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.LocalError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.MessageAlreadyDelivered
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.MessageNotFound
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.NotAuthorized
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.RemoteUnreachable
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError as RepositoryError

class DeleteMessageUseCase(
    private val chat: Chat,
    private val messageId: MessageId,
    private val currentUser: Participant,
    private val deleteMode: DeleteMessageMode,
    private val repository: Repository,
    private val now: Instant = Clock.System.now(),
) {

    @Suppress("ReturnCount")
    suspend operator fun invoke(): ResultWithError<Unit, DeleteMessageError> {
        val message = chat.messages.find { it.id == messageId }
            ?: return Failure(MessageNotFound(messageId))

        checkRules(
            message,
            hasAdminPrivileges = currentUser.isAdmin,
            hasModeratorPrivileges = currentUser.isModerator,
        ).let { result ->
            if (result is Failure) return result
        }

        return repository.deleteMessage(messageId, deleteMode).let { result ->
            when (result) {
                is Success -> Success(Unit)
                is Failure -> Failure(
                    when (result.error) {
                        RepositoryError.LocalError -> LocalError
                        is RepositoryError.MessageNotFound -> MessageNotFound(
                            result.error.messageId,
                        )
                        RepositoryError.NetworkNotAvailable -> NetworkNotAvailable
                        RepositoryError.RemoteError -> RemoteError
                        RepositoryError.RemoteUnreachable -> RemoteUnreachable
                    },
                )
            }
        }
    }

    private fun checkRules(
        message: Message,
        hasAdminPrivileges: Boolean,
        hasModeratorPrivileges: Boolean,
    ): ResultWithError<Unit, DeleteMessageError> {
        val deleteRules = chat.rules.filterIsInstance<DeleteMessageRule>()

        if (hasAdminPrivileges || hasModeratorPrivileges) {
            return checkRulesForAdminOrModerator(deleteRules, message)
        }

        return checkRulesForRegularUser(deleteRules, message)
    }

    private fun checkRulesForRegularUser(
        deleteRules: List<DeleteMessageRule>,
        message: Message,
    ): ResultWithError<Unit, DeleteMessageError> {
        for (rule in deleteRules) {
            val ruleResult = when (rule) {
                is DeleteWindow -> checkDeleteWindow(rule, message)
                SenderCanDeleteOwn -> checkSenderPermission(message)
                AdminCanDeleteAny -> Success(Unit) // Already handled for admin/moderator
                ModeratorCanDeleteAny -> Success(Unit) // Already handled for admin/moderator
                NoDeleteAfterDelivered -> checkDeliveryStatus(message)
                is DeleteForEveryoneWindow -> checkDeleteForEveryoneWindow(rule, message)
            }

            if (ruleResult is Failure) {
                return ruleResult
            }
        }
        return Success(Unit)
    }

    private fun checkRulesForAdminOrModerator(
        deleteRules: List<DeleteMessageRule>,
        message: Message,
    ): ResultWithError<Unit, DeleteMessageError> {
        for (rule in deleteRules) {
            val ruleResult = when (rule) {
                is DeleteWindow -> checkDeleteWindow(rule, message)
                NoDeleteAfterDelivered -> checkDeliveryStatus(message)
                is DeleteForEveryoneWindow -> checkDeleteForEveryoneWindow(rule, message)
                // Skip permission rules for admin/moderator
                SenderCanDeleteOwn -> Success(Unit)
                AdminCanDeleteAny -> Success(Unit)
                ModeratorCanDeleteAny -> Success(Unit)
            }

            if (ruleResult is Failure) {
                return ruleResult
            }
        }
        return Success(Unit)
    }

    private fun checkDeleteWindow(
        rule: DeleteWindow,
        message: Message,
    ): ResultWithError<Unit, DeleteMessageError> {
        val timeFromCreation = now - message.createdAt
        return if (timeFromCreation > rule.duration) {
            Failure(DeleteWindowExpired(rule.duration))
        } else {
            Success(Unit)
        }
    }

    private fun checkSenderPermission(message: Message): ResultWithError<Unit, DeleteMessageError> =
        if (message.sender.id == currentUser.id) {
            Success(Unit)
        } else {
            Failure(NotAuthorized)
        }

    private fun checkDeliveryStatus(message: Message): ResultWithError<Unit, DeleteMessageError> =
        if (message.deliveryStatus == DeliveryStatus.Delivered) {
            Failure(MessageAlreadyDelivered)
        } else {
            Success(Unit)
        }

    private fun checkDeleteForEveryoneWindow(
        rule: DeleteForEveryoneWindow,
        message: Message,
    ): ResultWithError<Unit, DeleteMessageError> {
        if (deleteMode == FOR_SENDER_ONLY) return Success(Unit)

        val timeFromCreation = now - message.createdAt
        return if (timeFromCreation > rule.duration) {
            Failure(DeleteForEveryoneWindowExpired(rule.duration))
        } else {
            Success(Unit)
        }
    }
}
