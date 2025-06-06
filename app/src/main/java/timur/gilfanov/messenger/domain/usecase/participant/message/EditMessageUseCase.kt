package timur.gilfanov.messenger.domain.usecase.participant.message

import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.CreationTimeCanNotChange
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.EditWindow
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.RecipientCanNotChange
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.SenderIdCanNotChange
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.CreationTimeChanged
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.EditWindowExpired
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.RecipientChanged
import timur.gilfanov.messenger.domain.usecase.participant.message.EditMessageError.SenderIdChanged

class EditMessageUseCase(
    val repository: ParticipantRepository,
    val deliveryStatusValidator: DeliveryStatusValidator,
) {

    operator fun invoke(chat: Chat, message: Message, now: Instant = Clock.System.now()) =
        flow<ResultWithError<Message, EditMessageError>> {
            checkRules(chat, message, now).onFailure { error: EditMessageError ->
                emit(Failure(error))
                return@flow
            }

            val validation = message.validate()
            if (validation is Failure) {
                emit(Failure(MessageIsNotValid(validation.error)))
                return@flow
            }

            message.deliveryStatus?.let { status ->
                emit(Failure(DeliveryStatusAlreadySet(status)))
                return@flow
            }

            var prev = message.deliveryStatus
            repository.editMessage(message).collect { progress ->
                deliveryStatusValidator.validate(prev, progress.deliveryStatus)
                    .onSuccess { emit(Success(progress)) }
                    .onFailure { error ->
                        emit(Failure(DeliveryStatusUpdateNotValid(error)))
                        return@collect
                    }
                prev = progress.deliveryStatus
            }
        }

    fun checkRules(
        chat: Chat,
        message: Message,
        now: Instant,
    ): ResultWithError<Unit, EditMessageError> {
        chat.rules.forEach { rule ->
            if (rule !is EditMessageRule) return@forEach
            val ruleResult = when (rule) {
                SenderIdCanNotChange -> checkSenderId(chat, message)
                RecipientCanNotChange -> checkRecipient(chat, message)
                CreationTimeCanNotChange -> checkCreationTime(chat, message)
                is EditWindow -> checkEditWindow(chat, message, rule, now)
            }

            if (ruleResult is Failure) {
                return ruleResult
            }
        }
        return Success(Unit)
    }

    private fun checkEditWindow(
        chat: Chat,
        message: Message,
        rule: EditWindow,
        now: Instant,
    ): ResultWithError<Unit, EditMessageError> {
        val messageCreatedAt = chat.messages.first { it.id == message.id }.createdAt
        val timeFromCreating = now - messageCreatedAt

        return if (timeFromCreating > rule.duration) {
            Failure(EditWindowExpired)
        } else {
            Success(Unit)
        }
    }

    private fun checkCreationTime(
        chat: Chat,
        message: Message,
    ): ResultWithError<Unit, EditMessageError> {
        val originalMessage = chat.messages.first { it.id == message.id }
        return if (originalMessage.createdAt != message.createdAt) {
            Failure(CreationTimeChanged)
        } else {
            Success(Unit)
        }
    }

    private fun checkRecipient(
        chat: Chat,
        message: Message,
    ): ResultWithError<Unit, EditMessageError> {
        val originalMessage = chat.messages.first { it.id == message.id }
        return if (originalMessage.recipient != message.recipient) {
            Failure(RecipientChanged)
        } else {
            Success(Unit)
        }
    }

    private fun checkSenderId(
        chat: Chat,
        message: Message,
    ): ResultWithError<Unit, EditMessageError> {
        val originalMessage = chat.messages.first { it.id == message.id }
        return if (originalMessage.sender.id != message.sender.id) {
            Failure(SenderIdChanged)
        } else {
            Success(Unit)
        }
    }
}
