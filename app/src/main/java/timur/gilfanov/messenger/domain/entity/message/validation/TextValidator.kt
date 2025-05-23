package timur.gilfanov.messenger.domain.entity.message.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError

class TextValidator(private val maxLength: Int) {
    fun validate(text: String): ResultWithError<Unit, TextValidationError> = when {
        text.isBlank() -> ResultWithError.Failure(TextValidationError.Empty)
        text.length > maxLength -> ResultWithError.Failure(TextValidationError.TooLong(maxLength))
        else -> ResultWithError.Success(Unit)
    }
}
