package timur.gilfanov.messenger.domain.usecase.participant.message

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
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageError.DeleteForEveryoneWindowExpired
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageError.DeleteWindowExpired
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageError.MessageAlreadyDelivered
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageError.NotAuthorized
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode.FOR_SENDER_ONLY
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryDeleteMessageError.MessageNotFound

class DeleteMessageUseCase(val repository: ParticipantRepository) {

    @Suppress("ReturnCount")
    suspend operator fun invoke(
        chat: Chat,
        messageId: MessageId,
        deleteMode: DeleteMessageMode,
        currentUser: Participant,
        now: Instant = Clock.System.now(),
    ): ResultWithError<Unit, DeleteMessageError> {
        val message = chat.messages.find { it.id == messageId }
            ?: return Failure(MessageNotFound(messageId))

        checkRules(
            chat = chat,
            message = message,
            deleteMode = deleteMode,
            now = now,
            currentUser,
            hasAdminPrivileges = currentUser.isAdmin,
            hasModeratorPrivileges = currentUser.isModerator,
        ).let { result ->
            if (result is Failure) return result
        }

        return repository.deleteMessage(messageId, deleteMode).let { result ->
            when (result) {
                is Success -> Success(Unit)
                is Failure -> Failure(result.error)
            }
        }
    }

    private fun checkRules(
        chat: Chat,
        message: Message,
        deleteMode: DeleteMessageMode,
        now: Instant,
        currentUser: Participant,
        hasAdminPrivileges: Boolean,
        hasModeratorPrivileges: Boolean,
    ): ResultWithError<Unit, DeleteMessageError> {
        val deleteRules = chat.rules.filterIsInstance<DeleteMessageRule>()

        // TODO check for privilege to delete others' messages.
        //  But still can't delete admin messages. Maybe add a separate rule for that.
        if (hasAdminPrivileges || hasModeratorPrivileges) {
            return checkRulesForAdminOrModerator(deleteRules, message, deleteMode, now)
        }

        return checkRulesForRegularUser(deleteRules, message, deleteMode, currentUser.id, now)
    }

    private fun checkRulesForRegularUser(
        deleteRules: List<DeleteMessageRule>,
        message: Message,
        deleteMode: DeleteMessageMode,
        participantId: ParticipantId,
        now: Instant,
    ): ResultWithError<Unit, DeleteMessageError> {
        for (rule in deleteRules) {
            val ruleResult = when (rule) {
                is DeleteWindow -> checkDeleteWindow(rule, message, now)
                SenderCanDeleteOwn -> checkSenderPermission(message, participantId)
                AdminCanDeleteAny -> Success(Unit) // Already handled for admin/moderator
                ModeratorCanDeleteAny -> Success(Unit) // Already handled for admin/moderator
                NoDeleteAfterDelivered -> checkDeliveryStatus(message)
                is DeleteForEveryoneWindow -> checkDeleteForEveryoneWindow(
                    rule,
                    message,
                    deleteMode,
                    now,
                )
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
        deleteMode: DeleteMessageMode,
        now: Instant,
    ): ResultWithError<Unit, DeleteMessageError> {
        for (rule in deleteRules) {
            val ruleResult = when (rule) {
                is DeleteWindow -> checkDeleteWindow(rule, message, now)
                NoDeleteAfterDelivered -> checkDeliveryStatus(message)
                is DeleteForEveryoneWindow -> checkDeleteForEveryoneWindow(
                    rule,
                    message,
                    deleteMode,
                    now,
                )
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
        now: Instant,
    ): ResultWithError<Unit, DeleteMessageError> {
        val timeFromCreation = now - message.createdAt
        return if (timeFromCreation > rule.duration) {
            Failure(DeleteWindowExpired(rule.duration))
        } else {
            Success(Unit)
        }
    }

    private fun checkSenderPermission(
        message: Message,
        participantId: ParticipantId,
    ): ResultWithError<Unit, DeleteMessageError> = if (message.sender.id == participantId) {
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
        deleteMode: DeleteMessageMode,
        now: Instant,
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
