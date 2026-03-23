package timur.gilfanov.messenger.auth.validation

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError

class ProfileNameValidatorStub(var error: ProfileNameValidationError? = null) :
    ProfileNameValidator {

    override fun validate(name: String): ResultWithError<Unit, ProfileNameValidationError> {
        val localError = error
        return if (localError == null) {
            ResultWithError.Success(Unit)
        } else {
            ResultWithError.Failure(localError)
        }
    }
}
