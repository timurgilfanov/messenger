package timur.gilfanov.messenger.domain.entity.chat.validation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.EmptyName
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NoParticipants
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NonEmptyMessages
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NonNullLastReadMessageId
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NonZeroUnreadCount
import timur.gilfanov.messenger.domain.entity.isFailure
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

class ChatValidator {
    fun validate(chat: Chat): ResultWithError<Unit, ChatValidationError> {
        val participantsResult = validateParticipants(chat.participants)
        if (participantsResult.isFailure) return participantsResult

        val nameResult = validateName(chat.name)
        if (nameResult.isFailure) return nameResult

        return ResultWithError.Success(Unit)
    }

    fun validateOnCreation(chat: Chat): ResultWithError<Unit, ChatValidationError> {
        val validationResult = validate(chat)
        if (validationResult.isFailure) return validationResult

        val messagesResult = validateMessagesOnCreation(chat.messages)
        if (messagesResult.isFailure) return messagesResult

        val unreadCountResult = validateUnreadMessagesCountOnCreation(chat.unreadMessagesCount)
        if (unreadCountResult.isFailure) return unreadCountResult

        val lastReadMessageResult = validateLastReadMessageIdOnCreation(chat.lastReadMessageId)
        if (lastReadMessageResult.isFailure) return lastReadMessageResult

        return ResultWithError.Success(Unit)
    }

    fun validateParticipants(
        participants: ImmutableSet<Participant>,
    ): ResultWithError<Unit, ChatValidationError> = if (participants.isEmpty()) {
        ResultWithError.Failure(NoParticipants)
    } else {
        ResultWithError.Success(Unit)
    }

    fun validateName(name: String): ResultWithError<Unit, ChatValidationError> =
        if (name.isBlank()) {
            ResultWithError.Failure(EmptyName)
        } else {
            ResultWithError.Success(Unit)
        }

    fun validateMessagesOnCreation(
        messages: ImmutableList<Message>,
    ): ResultWithError<Unit, ChatValidationError> = if (messages.isNotEmpty()) {
        ResultWithError.Failure(NonEmptyMessages)
    } else {
        ResultWithError.Success(Unit)
    }

    fun validateUnreadMessagesCountOnCreation(
        unreadMessagesCount: Int,
    ): ResultWithError<Unit, ChatValidationError> = if (unreadMessagesCount != 0) {
        ResultWithError.Failure(NonZeroUnreadCount)
    } else {
        ResultWithError.Success(Unit)
    }

    fun validateLastReadMessageIdOnCreation(
        lastReadMessageId: MessageId?,
    ): ResultWithError<Unit, ChatValidationError> = if (lastReadMessageId != null) {
        ResultWithError.Failure(NonNullLastReadMessageId)
    } else {
        ResultWithError.Success(Unit)
    }
}
