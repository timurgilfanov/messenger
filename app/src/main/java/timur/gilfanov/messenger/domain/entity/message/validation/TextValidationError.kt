package timur.gilfanov.messenger.domain.entity.message.validation

import timur.gilfanov.messenger.domain.usecase.ValidationError

sealed class TextValidationError : ValidationError {
    object Empty : TextValidationError()
    data class TooLong(val maxLength: Int) : TextValidationError()
}
