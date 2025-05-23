package timur.gilfanov.messenger.domain.usecase

import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError

sealed class CreateChatError {
    data class ChatIsNotValid(val error: ChatValidationError) : CreateChatError()
    data class RepositoryCreateChatError(val error: RepositoryError) : CreateChatError()
}
