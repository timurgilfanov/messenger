package timur.gilfanov.messenger.domain.usecase.privileged

import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError

sealed class CreateChatError

data class ChatIsNotValid(val error: ChatValidationError) : CreateChatError()

sealed class RepositoryCreateChatError : CreateChatError() {
    object NetworkNotAvailable : RepositoryCreateChatError()
    object ServerUnreachable : RepositoryCreateChatError()
    object ServerError : RepositoryCreateChatError()
    object UnknownError : RepositoryCreateChatError()
    object DuplicateChatId : RepositoryCreateChatError()
}
