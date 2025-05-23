package timur.gilfanov.messenger.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidator
import timur.gilfanov.messenger.domain.usecase.CreateChatError.ChatIsNotValid
import timur.gilfanov.messenger.domain.usecase.CreateChatError.RepositoryCreateChatError

class CreateChatUseCase(
    private val chat: Chat,
    private val repository: Repository,
    private val validator: ChatValidator = ChatValidator(),
) {
    suspend operator fun invoke(): ResultWithError<Chat, CreateChatError> {
        val validation = validator.validateOnCreation(chat)
        if (validation is Failure) {
            return Failure(ChatIsNotValid(validation.error))
        }

        val result = repository.createChat(chat)
        return when (result) {
            is Success -> Success(result.data)
            is Failure -> Failure(RepositoryCreateChatError(result.error))
        }
    }
}
