package timur.gilfanov.messenger.domain.usecase.participant.message

import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule.CanNotWriteAfterJoining
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule.Debounce
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.onFailure
import timur.gilfanov.messenger.domain.entity.onSuccess
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError.WaitAfterJoining
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError.WaitDebounce

class SendMessageUseCase(
    val repository: ParticipantRepository,
    val deliveryStatusValidator: DeliveryStatusValidator,
) {

    operator fun invoke(chat: Chat, message: Message, now: Instant = Clock.System.now()) =
        flow<ResultWithError<Message, SendMessageError>> {
            checkRules(chat, message, now).onFailure { error: SendMessageError ->
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
            repository.sendMessage(message).collect { progress ->
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
    ): ResultWithError<Unit, SendMessageError> {
        for (rule in chat.rules) {
            if (rule !is CreateMessageRule) continue
            val ruleResult = when (rule) {
                is CanNotWriteAfterJoining -> checkJoiningRule(chat, message, now, rule)
                is Debounce -> checkDebounceRule(chat, message, now, rule)
            }

            if (ruleResult is Failure) {
                return ruleResult
            }
        }
        return Success(Unit)
    }

    private fun checkJoiningRule(
        chat: Chat,
        message: Message,
        now: Instant,
        rule: CanNotWriteAfterJoining,
    ): ResultWithError<Unit, SendMessageError> {
        val joinedAt = chat.participants.first { it.id == message.sender.id }.joinedAt
        val timeFromJoining = now - joinedAt

        return if (timeFromJoining < rule.duration) {
            Failure(WaitAfterJoining(rule.duration - timeFromJoining))
        } else {
            Success(Unit)
        }
    }

    private fun checkDebounceRule(
        chat: Chat,
        message: Message,
        now: Instant,
        rule: Debounce,
    ): ResultWithError<Unit, SendMessageError> {
        val lastMessage = chat.lastMessageBy(message.sender.id) ?: return Success(Unit)
        val timeFromLastMessage = now - lastMessage.createdAt

        return if (timeFromLastMessage < rule.delay) {
            Failure(WaitDebounce(rule.delay - timeFromLastMessage))
        } else {
            Success(Unit)
        }
    }

    private fun Chat.lastMessageBy(participantId: ParticipantId): Message? = messages
        .filter { it.sender.id == participantId }
        .maxByOrNull { it.createdAt }
}
