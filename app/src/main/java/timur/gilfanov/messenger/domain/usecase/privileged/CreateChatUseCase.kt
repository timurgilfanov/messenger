package timur.gilfanov.messenger.domain.usecase.privileged

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidatorImpl

class CreateChatUseCase(
    private val repository: PrivilegedRepository,
    private val validator: ChatValidator = ChatValidatorImpl(),
) {
    suspend operator fun invoke(chat: Chat): ResultWithError<Chat, CreateChatError> {
        val validation = validator.validateOnCreation(chat)
        if (validation is Failure) {
            return Failure(ChatIsNotValid(validation.error))
        }

        val result = repository.createChat(chat)
        return when (result) {
            is Success -> Success(result.data)
            is Failure -> Failure(result.error)
        }
    }
}
