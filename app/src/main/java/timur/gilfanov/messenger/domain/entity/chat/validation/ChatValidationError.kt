package timur.gilfanov.messenger.domain.entity.chat.validation

import timur.gilfanov.messenger.domain.usecase.ValidationError

sealed class ChatValidationError : ValidationError {
    object NoParticipants : ChatValidationError()
    object EmptyName : ChatValidationError()
    object NonEmptyMessages : ChatValidationError()
    object NonZeroUnreadCount : ChatValidationError()
    object NonNullLastReadMessageId : ChatValidationError()
}
