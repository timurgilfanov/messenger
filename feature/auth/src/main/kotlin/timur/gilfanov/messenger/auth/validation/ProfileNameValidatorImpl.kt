package timur.gilfanov.messenger.auth.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

class ProfileNameValidatorImpl : ProfileNameValidator {

    companion object {
        const val MIN_NAME_LENGTH = 1
        const val MAX_NAME_LENGTH = 50
    }

    override fun validate(name: String): ResultWithError<Unit, ProfileNameValidationError> = when {
        name.isBlank() || name.length < MIN_NAME_LENGTH ->
            ResultWithError.Failure(
                ProfileNameValidationError.LengthOutOfBounds(
                    length = name.trim().length,
                    min = MIN_NAME_LENGTH,
                    max = MAX_NAME_LENGTH,
                ),
            )
        name.length > MAX_NAME_LENGTH ->
            ResultWithError.Failure(
                ProfileNameValidationError.LengthOutOfBounds(
                    length = name.length,
                    min = MIN_NAME_LENGTH,
                    max = MAX_NAME_LENGTH,
                ),
            )
        else -> ResultWithError.Success(Unit)
    }
}
