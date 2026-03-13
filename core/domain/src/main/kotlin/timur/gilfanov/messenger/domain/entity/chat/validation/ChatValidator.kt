package timur.gilfanov.messenger.domain.entity.chat.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat

interface ChatValidator {
    fun validateOnCreation(chat: Chat): ResultWithError<Unit, ChatValidationError>
}
